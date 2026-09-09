import { deflateSync } from 'node:zlib'
// Self-contained synthetic raster fixture; no private screenshots or external files.
export function pngFixture() {
  const crc = b => { let c=0xffffffff; for(const x of b) { c^=x; for(let i=0;i<8;i++) c=(c>>>1)^((c&1)?0xedb88320:0) } return (c^0xffffffff)>>>0 }
  const chunk = (type, data) => { const t=Buffer.from(type),n=Buffer.alloc(4),sum=Buffer.alloc(4);n.writeUInt32BE(data.length);sum.writeUInt32BE(crc(Buffer.concat([t,data])));return Buffer.concat([n,t,data,sum]) }
  const header=Buffer.alloc(13);header.writeUInt32BE(120,0);header.writeUInt32BE(64,4);header[8]=8;header[9]=2
  const pixels=Buffer.alloc(64*(120*3+1));for(let y=0;y<64;y++)for(let x=0;x<120;x++){const i=y*361+1+x*3;pixels[i]=50+x;pixels[i+1]=110+y;pixels[i+2]=180}
  return Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]),chunk('IHDR',header),chunk('IDAT',deflateSync(pixels)),chunk('IEND',Buffer.alloc(0))])
}
