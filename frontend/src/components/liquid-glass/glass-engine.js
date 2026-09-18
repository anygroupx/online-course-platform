/**
 * Reference DOM backdrop optics engine derived from zsio/liquid-glass@17188b0.
 * Canvas encodes material parameters, never a screenshot.
 */

/** @typedef {'auto' | 'svg' | 'css'} GlassMode */
/**
 * @typedef {Object} GlassOptions
 * @property {number} [radius]
 * @property {number} [refraction]
 * @property {number} [bevel]
 * @property {number} [blur]
 * @property {number} [dispersion]
 * @property {string} [tint]
 * @property {number} [fallbackBlur]
 * @property {number} [saturation]
 * @property {GlassMode} [mode]
 */
/**
 * @typedef {Object} GlassController
 * @property {(options: GlassOptions) => void} update
 * @property {() => void} refresh
 * @property {() => void} destroy
 * @property {'svg' | 'css' | 'solid'} renderer
 */

const positionHandlers = new WeakMap();

/**
 * Set the translation coordinates of a liquid glass host element.
 * @param {HTMLElement} host
 * @param {number} x
 * @param {number} y
 */
export function setLiquidGlassPosition(host, x, y) {
  if (Number.isFinite(x) && Number.isFinite(y)) {
    positionHandlers.get(host)?.(x, y);
  }
}

const NS = 'http://www.w3.org/2000/svg';

export const GLASS_DEFAULTS = {
  radius: 40,
  refraction: 56,
  bevel: 22,
  blur: 0.35,
  dispersion: 1.2,
  tint: 'rgba(255,255,255,.018)',
  fallbackBlur: 16,
  saturation: 1.08,
  mode: 'auto'
};

const clamp = (n, a, b) => Math.max(a, Math.min(n, b));

function number(v, fallback, a, b) {
  return typeof v === 'number' && Number.isFinite(v) ? clamp(v, a, b) : fallback;
}

function normalize(v = {}) {
  const d = GLASS_DEFAULTS;
  return {
    radius: number(v.radius, d.radius, 0, 10000),
    refraction: number(v.refraction, d.refraction, 0, 100),
    bevel: number(v.bevel, d.bevel, 2, 100),
    blur: number(v.blur, d.blur, 0, 24),
    dispersion: number(v.dispersion, d.dispersion, 0, 5),
    tint: v.tint ?? d.tint,
    fallbackBlur: number(v.fallbackBlur, d.fallbackBlur, 5, 32),
    saturation: number(v.saturation, d.saturation, 0.8, 1.6),
    mode: v.mode === 'svg' || v.mode === 'css' ? v.mode : 'auto'
  };
}

let svgBackdropCache = null;

export function useSVGBackdrop() {
  if (svgBackdropCache === null) {
    if (typeof navigator === 'undefined' || typeof CSS === 'undefined') return false;
    const ua = navigator.userAgent;
    const ios = /iPad|iPhone|iPod/.test(ua) || (/Macintosh/.test(ua) && navigator.maxTouchPoints > 1);
    svgBackdropCache = !ios && /Chrome\/|Chromium\/|Edg\//.test(ua) && CSS.supports('backdrop-filter', 'url("#probe")');
  }
  return svgBackdropCache;
}

function element(tag, attrs = {}) {
  const el = document.createElementNS(NS, tag);
  Object.entries(attrs).forEach(([k, v]) => el.setAttribute(k, v));
  return el;
}

const cache = new Map();
const emptyDisplacement = new Uint8ClampedArray(0);
const scratch = {
  canvas: null,
  w: 0,
  h: 0,
  map: null,
  light: null,
  rim: null
};

function textureCacheKey(width, height, radius, bevel, refract = true) {
  const density = Math.min(Math.max(2, Math.min(globalThis.devicePixelRatio || 1, 2.5)), 1536 / Math.max(width, height));
  const w = Math.max(2, Math.ceil(width * density)), h = Math.max(2, Math.ceil(height * density));
  return [width, height, radius, bevel, w, h, refract].join('/');
}

function fillTextures(md, ld, rd, w, h, width, height, radius, bevel, density, refract = true) {
  const clampFn = (n, a, b) => Math.max(a, Math.min(n, b));
  const PROFILE_STEPS = 1024;
  const profile = refract ? Float32Array.from({ length: PROFILE_STEPS + 1 }, (_, i) => {
    const t = i / PROFILE_STEPS;
    const sinIncident = Math.pow(1 - t, 1.28) * 0.985;
    const incident = Math.asin(sinIncident), transmitted = Math.asin(sinIncident / 1.46);
    const thickness = 0.72 + 0.28 * Math.sqrt(Math.max(0, 1 - (1 - t) * (1 - t)));
    const edge = Math.tan(Math.asin(0.985) - Math.asin(0.985 / 1.46)) * 0.72;
    return 0.92 * Math.tan(incident - transmitted) * thickness / edge;
  }) : null;
  const r = Math.min(radius, width / 2, height / 2), b = Math.min(bevel, width / 2, height / 2);
  if (refract) new Uint32Array(md.buffer).fill(new Uint32Array(new Uint8Array([128, 128, 0, 255]).buffer)[0]);
  const band = b + 6;
  const qx = new Float64Array(w), sx = new Float64Array(w), qy = new Float64Array(h), sy = new Float64Array(h);
  for (let x = 0; x < w; x++) {
    const px = (x + 0.5) / w * width - width / 2;
    qx[x] = Math.abs(px) - (width / 2 - r);
    sx[x] = Math.sign(px);
  }
  for (let y = 0; y < h; y++) {
    const py = (y + 0.5) / h * height - height / 2;
    qy[y] = Math.abs(py) - (height / 2 - r);
    sy[y] = Math.sign(py);
  }
  for (let y = 0; y < h; y++) {
    const rowQ = qy[y], oy = Math.max(rowQ, 0), oy2 = oy * oy, rowS = sy[y];
    let i = y * w * 4;
    for (let x = 0; x < w; x++, i += 4) {
      const colQ = qx[x], ox = Math.max(colQ, 0), len = Math.sqrt(ox * ox + oy2);
      const d = len + Math.min(Math.max(colQ, rowQ), 0) - r;
      if (-d > band) continue;
      let nx = 0, ny = 0;
      if (len > 0.0001) {
        nx = ox / len * sx[x];
        ny = oy / len * rowS;
      } else if (colQ > rowQ) nx = sx[x];
      else ny = rowS;
      const s = Math.max(0, -d);
      if (refract) {
        const t = clampFn(s / b, 0, 1), ti = t * PROFILE_STEPS, j = Math.min(PROFILE_STEPS - 1, Math.floor(ti));
        const bend = profile[j] + (profile[j + 1] - profile[j]) * (ti - j);
        md[i] = Math.round(127.5 - nx * bend * 127.5);
        md[i + 1] = Math.round(127.5 - ny * bend * 127.5);
        md[i + 3] = 255;
      }
      if (d > 0) continue;
      const coverage = clampFn(0.5 - d * density, 0, 1);
      const top = Math.max(0, -nx * 0.40 - ny * 0.9165);
      const bottom = Math.max(0, nx * 0.22 + ny * 0.9755);
      const side = Math.max(0, nx * 0.95 - ny * 0.31);
      const e1 = (s - 0.72) / 0.60, edge = Math.exp(-e1 * e1);
      const i1 = (s - 2.5) / 1.32, inner = Math.exp(-i1 * i1);
      const h1 = (s - b * 0.31) / (b * 0.29), shoulder = Math.exp(-h1 * h1);
      const t2 = top * top, b2 = bottom * bottom;
      const white = edge * (0.08 + 0.78 * t2 + 0.64 * b2 * bottom * b2) + inner * 0.16 * t2 * t2 + shoulder * (0.033 * top + 0.054 * b2 * bottom);
      const s1 = (s - 1.8) / 0.86;
      const shade = Math.exp(-s1 * s1) * 0.21 * side + shoulder * 0.032 * side;
      const v = white - shade;
      ld[i] = v >= 0 ? 255 : 37;
      ld[i + 1] = v >= 0 ? 255 : 46;
      ld[i + 2] = v >= 0 ? 255 : 62;
      ld[i + 3] = Math.round(clampFn(Math.abs(v), 0, 0.96) * 255 * coverage);
      const g1 = (s - 1.25) / 1.15;
      if (refract) md[i + 2] = Math.round(255 * 0.20 * Math.exp(-g1 * g1) * coverage);
      rd[i] = rd[i + 1] = rd[i + 2] = 255;
      rd[i + 3] = Math.round(255 * coverage * (edge * 0.85 + inner * 0.20));
    }
  }
}

const WORKER_SOURCE = `const fill=(${fillTextures.toString()});let oc=null,ctx=null,map=null,light=null,rim=null;const empty=new Uint8ClampedArray(0);self.onmessage=async e=>{const d=e.data||{};try{const density=Math.min(Math.max(2,Math.min(d.dpr||1,2.5)),1536/Math.max(d.width,d.height));const w=Math.max(2,Math.ceil(d.width*density)),h=Math.max(2,Math.ceil(d.height*density));if(!oc||oc.width!==w||oc.height!==h){oc=new OffscreenCanvas(w,h);ctx=oc.getContext('2d');if(!ctx)throw new Error('Canvas 2D is unavailable.');map=null;light=ctx.createImageData(w,h);rim=ctx.createImageData(w,h);}if(d.refract&&!map)map=ctx.createImageData(w,h);light.data.fill(0);rim.data.fill(0);fill(d.refract?map.data:empty,light.data,rim.data,w,h,d.width,d.height,d.radius,d.bevel,density,d.refract);const read=async img=>{ctx.putImageData(img,0,0);return new FileReaderSync().readAsDataURL(await oc.convertToBlob());};self.postMessage({key:d.key,ckey:d.ckey,displacement:d.refract?await read(map):'',lighting:await read(light),rim:await read(rim)});}catch(err){self.postMessage({key:d.key,ckey:d.ckey,error:String((err&&err.message)||err)});}};`;

const textureListeners = new Set();
const texturePending = new Map();
let textureActive = null, textureTimer = 0;
let textureEngine = null, textureBroken = false, textureLive = 0, textureURL = '';

function pumpTextures() {
  if (textureActive || !textureEngine) return;
  for (const [owner, job] of texturePending) {
    texturePending.delete(owner);
    const hit = cache.get(job.ckey);
    if (hit) {
      owner(job.key, hit);
      continue;
    }
    textureActive = job;
    textureTimer = window.setTimeout(breakTextureWorker, 5000);
    try {
      textureEngine.postMessage(job);
    } catch {
      breakTextureWorker();
    }
    return;
  }
}

function requestTextures(owner, job) {
  if (textureActive?.key === job.key) texturePending.delete(owner);
  else texturePending.set(owner, job);
  pumpTextures();
}

function textureWorker() {
  try {
    if (textureBroken || typeof Worker === 'undefined' || typeof OffscreenCanvas === 'undefined') return null;
    if (!textureEngine) {
      textureURL = URL.createObjectURL(new Blob([WORKER_SOURCE], { type: 'text/javascript' }));
      textureEngine = new Worker(textureURL);
      textureEngine.onmessage = (e) => {
        const { key, ckey, displacement, lighting, rim, error } = (e.data || {});
        clearTimeout(textureTimer);
        textureActive = null;
        if (error || typeof displacement !== 'string') {
          breakTextureWorker();
          return;
        }
        const data = { displacement, lighting, rim };
        if (cache.size >= 24) cache.delete(cache.keys().next().value);
        cache.set(ckey, data);
        textureListeners.forEach(l => l(key, data));
        pumpTextures();
      };
      textureEngine.onerror = () => breakTextureWorker();
    }
    return textureEngine;
  } catch {
    textureBroken = true;
    return null;
  }
}

function breakTextureWorker() {
  textureBroken = true;
  texturePending.clear();
  textureActive = null;
  clearTimeout(textureTimer);
  if (textureEngine) {
    textureEngine.terminate();
    textureEngine = null;
  }
  if (textureURL) {
    URL.revokeObjectURL(textureURL);
    textureURL = '';
  }
  textureListeners.forEach(l => l('', null));
}

function optics(width, height, radius, bevel, refract = true) {
  const density = Math.min(Math.max(2, Math.min(globalThis.devicePixelRatio || 1, 2.5)), 1536 / Math.max(width, height));
  const w = Math.max(2, Math.ceil(width * density)), h = Math.max(2, Math.ceil(height * density));
  const key = textureCacheKey(width, height, radius, bevel, refract);
  const cached = cache.get(key);
  if (cached) {
    cache.delete(key);
    cache.set(key, cached);
    return cached;
  }
  if (!scratch.canvas) scratch.canvas = document.createElement('canvas');
  if (scratch.w !== w || scratch.h !== h) {
    scratch.canvas.width = w;
    scratch.canvas.height = h;
    const prime = scratch.canvas.getContext('2d');
    if (!prime) throw new Error('Canvas 2D is unavailable.');
    scratch.map = null;
    scratch.light = prime.createImageData(w, h);
    scratch.rim = prime.createImageData(w, h);
    scratch.w = w;
    scratch.h = h;
  }
  const canvas = scratch.canvas, ctx = canvas.getContext('2d');
  if (!ctx) throw new Error('Canvas 2D is unavailable.');
  if (refract && !scratch.map) scratch.map = ctx.createImageData(w, h);
  const map = scratch.map, light = scratch.light, rim = scratch.rim;
  const md = refract ? map.data : emptyDisplacement, ld = light.data, rd = rim.data;
  ld.fill(0);
  rd.fill(0);
  fillTextures(md, ld, rd, w, h, width, height, radius, bevel, density, refract);
  let displacement = '';
  if (refract) {
    ctx.putImageData(map, 0, 0);
    displacement = canvas.toDataURL();
  }
  ctx.putImageData(light, 0, 0);
  const lighting = canvas.toDataURL();
  ctx.putImageData(rim, 0, 0);
  const rimURL = canvas.toDataURL();
  const data = { displacement, lighting, rim: rimURL };
  if (cache.size >= 24) cache.delete(cache.keys().next().value);
  cache.set(key, data);
  return data;
}

/**
 * Mount Liquid Glass optics onto a host element containing the 4-layer topology.
 * @param {HTMLElement} host
 * @param {GlassOptions} [initial]
 * @returns {GlassController}
 */
export function mountLiquidGlass(host, initial = {}) {
  const surface = host.querySelector(':scope > .lg-surface');
  const lighting = host.querySelector(':scope > .lg-light');
  if (!surface || !lighting) throw new Error('Missing .lg-surface / .lg-light layer.');

  let options = normalize(initial), dead = false, failed = false, frame = 0, lightFrame = 0, geometry = '';
  let lastGen = -Infinity, followUp = 0, wantedGeometry = '';
  let appliedRadius = '', appliedTint = '', appliedFrame = '', appliedBlur = '', appliedScales = '', appliedBackdrop = '', appliedRenderer = '';
  let renderer = 'css';

  const id = 'lens-' + (globalThis.crypto?.randomUUID?.() ?? Math.random().toString(36).slice(2) + Date.now());
  const root = element('svg', { width: '0', height: '0', 'aria-hidden': 'true', 'data-lg-defs': '', focusable: 'false' });
  root.style.cssText = 'position:absolute;width:0;height:0;overflow:hidden;pointer-events:none';
  const defs = element('defs');
  const filter = element('filter', { id, filterUnits: 'userSpaceOnUse', primitiveUnits: 'userSpaceOnUse', 'color-interpolation-filters': 'sRGB' });
  const blur = element('feGaussianBlur', { in: 'SourceGraphic', stdDeviation: String(options.blur), result: 'source' });
  const map = element('feImage', { x: '0', y: '0', preserveAspectRatio: 'none', result: 'raw-map' });
  const mapSmooth = element('feGaussianBlur', { in: 'raw-map', stdDeviation: '.55', edgeMode: 'duplicate', result: 'map' });
  filter.append(blur, map, mapSmooth);
  const displacements = [];

  for (let c = 0; c < 3; c++) {
    const displacement = element('feDisplacementMap', { in: 'source', in2: 'map', scale: '0', xChannelSelector: 'R', yChannelSelector: 'G', result: 'd' + c });
    const m = Array(20).fill(0);
    m[c * 5 + c] = 1;
    m[18] = 1;
    filter.append(displacement, element('feColorMatrix', { in: 'd' + c, type: 'matrix', values: m.join(' '), result: 'c' + c }));
    displacements.push(displacement);
  }
  filter.append(
    element('feBlend', { in: 'c0', in2: 'c1', mode: 'screen', result: 'rg' }),
    element('feBlend', { in: 'rg', in2: 'c2', mode: 'screen', result: 'refracted' })
  );
  const reflected = element('feDisplacementMap', { in: 'source', in2: 'map', scale: '0', xChannelSelector: 'R', yChannelSelector: 'G', result: 'edge-sample' });
  const weight = element('feColorMatrix', { in: 'map', type: 'matrix', values: '0 0 0 0 0  0 0 0 0 0  0 0 0 0 0  0 0 1 0 0', result: 'edge-weight' });
  filter.append(
    reflected,
    weight,
    element('feComposite', { in: 'edge-sample', in2: 'edge-weight', operator: 'in', result: 'reflection' }),
    element('feBlend', { in: 'refracted', in2: 'reflection', mode: 'screen', result: 'optics' }),
    element('feGaussianBlur', { in: 'optics', stdDeviation: '.48' })
  );
  defs.append(filter);
  root.append(defs);
  document.body.append(root);

  const media = ['(prefers-reduced-transparency: reduce)', '(prefers-contrast: more)', '(forced-colors: active)'].map(q => matchMedia(q));
  const motion = matchMedia('(prefers-reduced-motion: reduce)');
  const sheen = host.querySelector(':scope > .lg-sheen');
  const owned = ['--lg-radius', '--lg-tint'];
  const saved = owned.map(k => [k, host.style.getPropertyValue(k)]);
  const savedRenderer = host.getAttribute('data-glass-renderer');
  const savedFilter = surface.style.backdropFilter;
  const savedWebkit = surface.style.getPropertyValue('-webkit-backdrop-filter');
  const savedLight = lighting.style.backgroundImage;
  const savedMask = lighting.style.getPropertyValue('--lg-rim-mask');
  const lightLayers = [lighting, sheen];
  const savedPointer = lightLayers.map(l => l ? ['--lg-pointer-x', '--lg-pointer-y'].map(k => [k, l.style.getPropertyValue(k)]) : []);

  function applyTextures(textures, w, h) {
    if (textures.displacement) {
      map.setAttribute('width', String(w));
      map.setAttribute('height', String(h));
      map.setAttribute('href', textures.displacement);
    }
    lighting.style.backgroundImage = `url("${textures.lighting}")`;
    lighting.style.setProperty('--lg-rim-mask', `url("${textures.rim}")`);
  }

  function onTexture(key, data) {
    if (!dead && (!data || key === wantedGeometry)) schedule();
  }

  function render() {
    if (dead) return;
    frame = 0;
    const w = host.offsetWidth, h = host.offsetHeight, r = Math.min(options.radius, w / 2, h / 2);
    const radiusPx = options.radius + 'px';
    if (radiusPx !== appliedRadius) {
      host.style.setProperty('--lg-radius', radiusPx);
      appliedRadius = radiusPx;
    }
    if (options.tint !== appliedTint) {
      host.style.setProperty('--lg-tint', options.tint);
      appliedTint = options.tint;
    }
    const accessible = media.some(m => m.matches);
    renderer = accessible ? 'solid' : (!failed && options.mode !== 'css' && (options.mode === 'svg' || useSVGBackdrop()) ? 'svg' : 'css');

    if (w > 0 && h > 0 && !failed && !accessible) {
      try {
        const refract = renderer === 'svg';
        const key = textureCacheKey(w, h, r, options.bevel, refract);
        wantedGeometry = key;
        if (key !== geometry) {
          const now = performance.now(), worker = textureWorker(), hit = cache.get(key);
          if (hit) {
            texturePending.delete(onTexture);
            applyTextures(hit, w, h);
            geometry = key;
          } else if (worker) {
            requestTextures(onTexture, { key, ckey: key, width: w, height: h, radius: r, bevel: options.bevel, dpr: globalThis.devicePixelRatio || 1, refract });
          } else if (now - lastGen >= 48) {
            lastGen = now;
            applyTextures(optics(w, h, r, options.bevel, refract), w, h);
            geometry = key;
          } else if (!followUp) {
            followUp = window.setTimeout(() => {
              followUp = 0;
              schedule();
            }, 48 - (now - lastGen));
          }
        } else texturePending.delete(onTexture);

        if (refract) {
          const pad = Math.ceil(options.refraction / 2 + options.blur * 3 + 4);
          const frameKey = pad + '/' + w + '/' + h;
          if (frameKey !== appliedFrame) {
            filter.setAttribute('x', String(-pad));
            filter.setAttribute('y', String(-pad));
            filter.setAttribute('width', String(w + pad * 2));
            filter.setAttribute('height', String(h + pad * 2));
            appliedFrame = frameKey;
          }
          const blurValue = String(options.blur);
          if (blurValue !== appliedBlur) {
            blur.setAttribute('stdDeviation', blurValue);
            appliedBlur = blurValue;
          }
          const strength = Math.min(options.refraction, Math.min(w, h) * 0.46);
          const separation = options.refraction === 0 ? 0 : Math.min(options.dispersion, strength * 0.10);
          const scaleKey = strength + '/' + separation;
          if (scaleKey !== appliedScales) {
            displacements.forEach((node, c) => node.setAttribute('scale', String(strength + (c - 1) * separation)));
            reflected.setAttribute('scale', String(-strength * 0.22));
            appliedScales = scaleKey;
          }
        }
      } catch (e) {
        failed = true;
        renderer = 'css';
        console.warn('[LiquidGlass] Using CSS fallback.', e);
      }
    }

    if (accessible || failed) {
      texturePending.delete(onTexture);
      wantedGeometry = '';
    }
    if (renderer === 'svg' && !map.getAttribute('href')) renderer = 'css';
    const value = renderer === 'solid'
      ? 'none'
      : renderer === 'svg'
        ? `url("#${id}")`
        : `blur(${options.fallbackBlur}px) saturate(${options.saturation})`;
    if (value !== appliedBackdrop) {
      surface.style.backdropFilter = value;
      surface.style.setProperty('-webkit-backdrop-filter', value);
      appliedBackdrop = value;
    }
    if (renderer !== appliedRenderer) {
      host.dataset.glassRenderer = renderer;
      appliedRenderer = renderer;
    }
  }

  function schedule() {
    if (!frame && !dead) frame = requestAnimationFrame(render);
  }

  let targetX = 28, targetY = 12, currentX = 28, currentY = 12, lastTime = 0, targetTime = 0;
  let pointerPosition = null;
  let pendingPosition = null, appliedPosition = null;
  let savedPosition = null, appliedPointerX = '', appliedPointerY = '';

  function queuePosition(x, y) {
    pendingPosition = { x, y };
    if (!lightFrame && !dead) lightFrame = requestAnimationFrame(animateLight);
  }

  function animateLight(time) {
    lightFrame = 0;
    if (dead) return;
    const next = pendingPosition;
    pendingPosition = null;
    if (pointerPosition) {
      const r = host.getBoundingClientRect(), p = pointerPosition;
      pointerPosition = null;
      const dx = next ? next.x - (appliedPosition?.x ?? host.offsetLeft) : 0;
      const dy = next ? next.y - (appliedPosition?.y ?? host.offsetTop) : 0;
      if (r.width && r.height) {
        targetX = clamp((p.x - r.left - dx) / r.width * 100, 0, 100);
        targetY = clamp((p.y - r.top - dy) / r.height * 100, 0, 100);
      }
    }
    if (next) {
      if (!savedPosition) {
        savedPosition = [host.style.left, host.style.top, host.style.transform];
        host.style.left = '0px';
        host.style.top = '0px';
      }
      if (!appliedPosition || next.x !== appliedPosition.x || next.y !== appliedPosition.y) {
        host.style.transform = `translate3d(${next.x}px, ${next.y}px, 0)`;
        appliedPosition = next;
      }
    }
    const mix = 1 - Math.exp(-Math.min(64, lastTime ? time - lastTime : 16) / 62);
    lastTime = time;
    currentX += (targetX - currentX) * mix;
    currentY += (targetY - currentY) * mix;
    if (time - targetTime > 450) {
      currentX = targetX;
      currentY = targetY;
    }
    const px = currentX.toFixed(3) + '%', py = currentY.toFixed(3) + '%';
    if (px !== appliedPointerX || py !== appliedPointerY) {
      lightLayers.forEach(l => {
        if (l) {
          if (px !== appliedPointerX) l.style.setProperty('--lg-pointer-x', px);
          if (py !== appliedPointerY) l.style.setProperty('--lg-pointer-y', py);
        }
      });
      appliedPointerX = px;
      appliedPointerY = py;
    }
    if (!motion.matches && renderer !== 'solid' && Math.abs(targetX - currentX) + Math.abs(targetY - currentY) > 0.04) {
      lightFrame = requestAnimationFrame(animateLight);
    } else {
      lastTime = 0;
    }
  }

  function moveLight(x, y) {
    targetX = x;
    targetY = y;
    targetTime = performance.now();
    if (!dead && !lightFrame && !motion.matches) lightFrame = requestAnimationFrame(animateLight);
  }

  function pointer(e) {
    if (motion.matches || renderer === 'solid') return;
    pointerPosition = { x: e.clientX, y: e.clientY };
    targetTime = performance.now();
    if (!lightFrame) lightFrame = requestAnimationFrame(animateLight);
  }

  function leave() {
    pointerPosition = null;
    moveLight(28, 12);
  }

  function onMotion() {
    if (motion.matches) {
      pointerPosition = null;
      cancelAnimationFrame(lightFrame);
      lightFrame = 0;
      currentX = targetX = 28;
      currentY = targetY = 12;
      lastTime = 0;
      lightLayers.forEach(l => {
        if (l) {
          l.style.setProperty('--lg-pointer-x', '28%');
          l.style.setProperty('--lg-pointer-y', '12%');
        }
      });
      appliedPointerX = '28%';
      appliedPointerY = '12%';
      if (pendingPosition && !dead) lightFrame = requestAnimationFrame(animateLight);
    }
  }

  const observer = new ResizeObserver(schedule);
  observer.observe(host);
  media.forEach(m => m.addEventListener('change', schedule));
  motion.addEventListener('change', onMotion);
  window.addEventListener('resize', schedule, { passive: true });
  host.addEventListener('pointermove', pointer, { passive: true });
  host.addEventListener('pointerleave', leave);
  positionHandlers.set(host, queuePosition);
  textureListeners.add(onTexture);
  textureLive++;
  render();

  return {
    get renderer() {
      return renderer;
    },
    update(v) {
      options = normalize({ ...options, ...v });
      schedule();
    },
    refresh: schedule,
    destroy() {
      if (dead) return;
      dead = true;
      cancelAnimationFrame(frame);
      cancelAnimationFrame(lightFrame);
      clearTimeout(followUp);
      observer.disconnect();
      media.forEach(m => m.removeEventListener('change', schedule));
      motion.removeEventListener('change', onMotion);
      window.removeEventListener('resize', schedule);
      host.removeEventListener('pointermove', pointer);
      host.removeEventListener('pointerleave', leave);
      root.remove();
      positionHandlers.delete(host);
      pendingPosition = null;
      pointerPosition = null;
      if (savedPosition) {
        [host.style.left, host.style.top, host.style.transform] = savedPosition;
      }
      textureListeners.delete(onTexture);
      texturePending.delete(onTexture);
      if (--textureLive === 0 && textureEngine) {
        textureEngine.terminate();
        textureEngine = null;
        texturePending.clear();
        textureActive = null;
        clearTimeout(textureTimer);
        if (textureURL) {
          URL.revokeObjectURL(textureURL);
          textureURL = '';
        }
      }
      saved.forEach(([k, v]) => v ? host.style.setProperty(k, v) : host.style.removeProperty(k));
      if (savedRenderer === null) delete host.dataset.glassRenderer;
      else host.dataset.glassRenderer = savedRenderer;
      surface.style.backdropFilter = savedFilter;
      surface.style.setProperty('-webkit-backdrop-filter', savedWebkit);
      lighting.style.backgroundImage = savedLight;
      if (savedMask) lighting.style.setProperty('--lg-rim-mask', savedMask);
      else lighting.style.removeProperty('--lg-rim-mask');
      lightLayers.forEach((l, i) => {
        if (!l) return;
        savedPointer[i].forEach(([k, v]) => v ? l.style.setProperty(k, v) : l.style.removeProperty(k));
      });
    }
  };
}
