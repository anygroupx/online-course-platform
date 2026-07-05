<template>
  <div class="guest-order-container">
    <!-- Particle Background -->
    <canvas id="particle-canvas" ref="canvasRef"></canvas>
    
    <!-- Ambient Glows -->
    <div class="glow-spot spot-1"></div>
    <div class="glow-spot spot-2"></div>

    <div class="content-wrapper">
      <div class="glass-card animate-in">
        <div class="card-header">
          <div class="logo-wrapper">
            <div class="logo-icon">
              <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M12 2L2 7L12 12L22 7L12 2Z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                <path d="M2 17L12 22L22 17" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                <path d="M2 12L12 17L22 12" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
            </div>
          </div>
          <h1>极速下单</h1>
          <p>选择平台 · 输入账号 · 坐等完成</p>
        </div>

        <form class="order-form" @submit.prevent="handleSubmit">
          <!-- Platform Selector -->
          <div class="form-item">
            <div class="custom-select" :class="{ open: isSelectOpen }" ref="selectRef">
              <div class="select-trigger" @click="toggleSelect">
                <div class="trigger-content">
                  <span v-if="selectedPlatform" class="selected-text">
                    {{ selectedPlatform.name }} 
                    <span class="price-tag">¥{{ (selectedPlatform.basePrice * personalPriceMultiplier).toFixed(2) }}</span>
                  </span>
                  <span v-else class="placeholder">请选择网课平台</span>
                </div>
                <i class="arrow-icon">
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><path d="M6 9l6 6 6-6"/></svg>
                </i>
              </div>
              <div class="select-options" v-show="isSelectOpen">
                <div 
                  v-for="platform in platformList" 
                  :key="platform.id" 
                  class="option-item"
                  @click="selectPlatform(platform)"
                >
                  <span class="platform-name">{{ platform.name }}</span>
                  <span class="platform-price">¥{{ (platform.basePrice * personalPriceMultiplier).toFixed(2) }}</span>
                </div>
              </div>
            </div>
          </div>

          <!-- Account Info -->
          <div class="input-row">
            <div class="form-item">
              <div class="input-wrapper">
                <input type="text" id="school" v-model="form.schoolName" placeholder=" " />
                <label for="school">学校名称</label>
                <div class="focus-border"></div>
              </div>
            </div>
            <div class="form-item">
              <div class="input-wrapper">
                <input type="text" id="account" v-model="form.studentAccount" required placeholder=" " />
                <label for="account">账号/学号</label>
                <div class="focus-border"></div>
              </div>
            </div>
          </div>

          <div class="form-item">
            <div class="input-wrapper">
              <input type="password" id="password" v-model="form.studentPassword" required placeholder=" " />
              <label for="password">登录密码</label>
              <div class="focus-border"></div>
            </div>
          </div>

          <div class="form-item">
            <div class="input-wrapper">
              <input type="text" id="courseName" v-model="form.courseName" placeholder=" " />
              <label for="courseName">课程名称 (选填，留空自动匹配)</label>
              <div class="focus-border"></div>
            </div>
          </div>

          <div class="form-item">
            <div class="input-wrapper">
              <input type="text" id="contact" v-model="form.contact" required placeholder=" " />
              <label for="contact">联系方式 (手机/QQ)</label>
              <div class="focus-border"></div>
            </div>
          </div>

          <!-- Payment Section -->
          <div class="payment-section">
            <span class="section-label">支付方式</span>
            <div class="payment-options">
              <label class="payment-option" :class="{ active: form.paymentMethod === 'alipay' }">
                <input type="radio" value="alipay" v-model="form.paymentMethod" />
                <div class="option-content">
                  <div class="icon-box alipay">
                    <svg viewBox="0 0 1024 1024" width="20" height="20"><path d="M876.8 688c-35.2 121.6-124.8 217.6-249.6 265.6-28.8-38.4-64-89.6-96-144 92.8-32 163.2-92.8 198.4-176h-96c-25.6 51.2-70.4 92.8-128 115.2-35.2 12.8-73.6 22.4-115.2 25.6-12.8 44.8-28.8 86.4-48 124.8-19.2 35.2-38.4 67.2-60.8 96C361.6 934.4 243.2 880 160 796.8c-3.2 16-6.4 32-6.4 48 0 16 0 32 3.2 48 6.4 35.2 22.4 67.2 44.8 96 51.2 60.8 124.8 96 208 96 160 0 297.6-115.2 329.6-272H876.8zM512 64c-16 0-32 12.8-32 32v96H224c-19.2 0-32 12.8-32 32s12.8 32 32 32h160v96h-96c-19.2 0-32 12.8-32 32s12.8 32 32 32h96v96c0 19.2 12.8 32 32 32s32-12.8 32-32v-96h160c19.2 0 32-12.8 32-32s-12.8-32-32-32h-160v-96h160c19.2 0 32-12.8 32-32s-12.8-32-32-32H544V96c0-19.2-12.8-32-32-32z" fill="currentColor"/></svg>
                  </div>
                  <span>支付宝</span>
                </div>
                <div class="active-glow"></div>
              </label>
              <label class="payment-option" :class="{ active: form.paymentMethod === 'wechat' }">
                <input type="radio" value="wechat" v-model="form.paymentMethod" />
                <div class="option-content">
                  <div class="icon-box wechat">
                    <svg viewBox="0 0 1024 1024" width="20" height="20"><path d="M672 624c0-108.8-99.2-198.4-224-198.4-128 0-224 89.6-224 198.4 0 108.8 99.2 198.4 224 198.4 25.6 0 51.2-3.2 73.6-9.6l60.8 32c9.6 6.4 22.4 3.2 25.6-6.4 3.2-3.2 3.2-6.4 0-9.6l-16-57.6c48-35.2 80-86.4 80-147.2zM384 544c-19.2 0-32-12.8-32-32s12.8-32 32-32 32 12.8 32 32-12.8 32-32 32z m128 0c-19.2 0-32-12.8-32-32s12.8-32 32-32 32 12.8 32 32-12.8 32-32 32z" fill="currentColor"/><path d="M880 352c0-134.4-118.4-243.2-265.6-243.2-147.2 0-265.6 108.8-265.6 243.2 0 134.4 118.4 243.2 265.6 243.2 28.8 0 57.6-3.2 86.4-9.6l70.4 38.4c9.6 6.4 22.4 3.2 25.6-9.6 3.2-3.2 3.2-6.4 0-9.6l-19.2-67.2c54.4-41.6 89.6-102.4 89.6-172.8zM544 256c-22.4 0-41.6-19.2-41.6-41.6s19.2-41.6 41.6-41.6 41.6 19.2 41.6 41.6S566.4 256 544 256z m160 0c-22.4 0-41.6-19.2-41.6-41.6s19.2-41.6 41.6-41.6 41.6 19.2 41.6 41.6S726.4 256 704 256z" fill="currentColor"/></svg>
                  </div>
                  <span>微信支付</span>
                </div>
                <div class="active-glow"></div>
              </label>
            </div>
          </div>

          <button type="submit" class="submit-btn" :class="{ loading: isLoading }">
            <span class="btn-content">
              <span class="btn-text">立即支付</span>
              <i class="btn-icon">→</i>
            </span>
            <div class="loader" v-if="isLoading"></div>
          </button>
        </form>
        
        <div class="card-footer">
          <a href="/login" class="login-link">已有账号？<span class="highlight">去登录</span></a>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue';
import { ElMessage } from 'element-plus';
import { getCoursePlatforms } from '@/api/course';
import { getUserInfo } from '@/api/user';

const isLoading = ref(false);
const isSelectOpen = ref(false);
const selectRef = ref(null);
const canvasRef = ref(null);
const platformList = ref([]);
const selectedPlatform = ref(null);
const personalPriceMultiplier = ref(1.0);

// Mouse Interaction State
const mouseX = ref(0);
const mouseY = ref(0);
const spot1Ref = ref(null);
const spot2Ref = ref(null);

const form = reactive({
  schoolName: '',
  studentAccount: '',
  studentPassword: '',
  courseName: '',
  contact: '',
  paymentMethod: 'alipay'
});

// --- Particle Animation System ---
let animationFrameId;
let particles = [];
const PARTICLE_COUNT = 60;
const CONNECTION_DISTANCE = 150;

class Particle {
  constructor(canvas) {
    this.canvas = canvas;
    this.x = Math.random() * canvas.width;
    this.y = Math.random() * canvas.height;
    this.vx = (Math.random() - 0.5) * 0.5;
    this.vy = (Math.random() - 0.5) * 0.5;
    this.size = Math.random() * 2 + 1;
    this.color = Math.random() > 0.5 ? 'rgba(168, 85, 247, ' : 'rgba(34, 211, 238, '; 
  }

  update() {
    this.x += this.vx;
    this.y += this.vy;

    if (this.x < 0 || this.x > this.canvas.width) this.vx *= -1;
    if (this.y < 0 || this.y > this.canvas.height) this.vy *= -1;
  }

  draw(ctx) {
    ctx.beginPath();
    ctx.arc(this.x, this.y, this.size, 0, Math.PI * 2);
    ctx.fillStyle = this.color + '0.5)';
    ctx.fill();
  }
}

const initParticles = () => {
  const canvas = canvasRef.value;
  if (!canvas) return;
  
  const ctx = canvas.getContext('2d');
  const resize = () => {
    canvas.width = window.innerWidth;
    canvas.height = window.innerHeight;
  };
  
  window.addEventListener('resize', resize);
  resize();

  particles = Array.from({ length: PARTICLE_COUNT }, () => new Particle(canvas));

  const animate = () => {
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    particles.forEach(p => {
      p.update();
      p.draw(ctx);
    });
    particles.forEach((p1, i) => {
      for (let j = i + 1; j < particles.length; j++) {
        const p2 = particles[j];
        const dx = p1.x - p2.x;
        const dy = p1.y - p2.y;
        const dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < CONNECTION_DISTANCE) {
          ctx.beginPath();
          ctx.strokeStyle = `rgba(255, 255, 255, ${0.1 * (1 - dist / CONNECTION_DISTANCE)})`;
          ctx.lineWidth = 1;
          ctx.moveTo(p1.x, p1.y);
          ctx.lineTo(p2.x, p2.y);
          ctx.stroke();
        }
      }
    });
    animationFrameId = requestAnimationFrame(animate);
  };
  animate();
};

// --- Mouse Avoidance Logic ---
const handleMouseMove = (e) => {
  mouseX.value = e.clientX;
  mouseY.value = e.clientY;
  
  // Simple avoidance calculation
  const moveSpot = (spotRef, speed) => {
    if (!spotRef) return;
    const rect = spotRef.getBoundingClientRect();
    const spotX = rect.left + rect.width / 2;
    const spotY = rect.top + rect.height / 2;
    
    const dx = mouseX.value - spotX;
    const dy = mouseY.value - spotY;
    const dist = Math.sqrt(dx * dx + dy * dy);
    const maxDist = 400;
    
    if (dist < maxDist) {
      const force = (maxDist - dist) / maxDist;
      const moveX = -dx * force * speed;
      const moveY = -dy * force * speed;
      spotRef.style.transform = `translate(${moveX}px, ${moveY}px)`;
    } else {
      spotRef.style.transform = `translate(0, 0)`;
    }
  };

  moveSpot(spot1Ref.value, 0.15);
  moveSpot(spot2Ref.value, 0.12);
};

const loadData = async () => {
  try {
    try {
      const userRes = await getUserInfo();
      if (userRes.code === 1 && userRes.data.rate) {
        personalPriceMultiplier.value = userRes.data.rate;
      }
    } catch (e) {}

    const res = await getCoursePlatforms();
    if (res.code === 1) {
      platformList.value = res.data;
    }
  } catch (error) {
    console.error("加载失败", error);
    ElMessage.error("加载平台列表失败");
  }
};

const toggleSelect = () => isSelectOpen.value = !isSelectOpen.value;
const selectPlatform = (platform) => {
  selectedPlatform.value = platform;
  isSelectOpen.value = false;
};

const handleClickOutside = (event) => {
  if (selectRef.value && !selectRef.value.contains(event.target)) {
    isSelectOpen.value = false;
  }
};

onMounted(() => {
  loadData();
  initParticles();
  document.addEventListener('click', handleClickOutside);
  window.addEventListener('mousemove', handleMouseMove);
});

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside);
  window.removeEventListener('mousemove', handleMouseMove);
  if (animationFrameId) cancelAnimationFrame(animationFrameId);
});

const handleSubmit = async () => {
  if (!selectedPlatform.value) {
    ElMessage.warning('请选择课程平台');
    return;
  }
  if (isLoading.value) return;
  
  isLoading.value = true;
  setTimeout(() => {
    isLoading.value = false;
    ElMessage.success({
      message: '订单创建成功！即将跳转支付...',
      type: 'success',
      plain: true,
    });
  }, 1500);
};
</script>

<style scoped lang="scss">
@import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap');

:root {
  --primary: #a78bfa; /* Lighter Violet */
  --primary-glow: rgba(167, 139, 250, 0.5);
  --accent: #22d3ee; /* Cyan 400 */
  --bg-dark: #0f172a; 
  --card-bg: rgba(15, 23, 42, 0.6);
  --input-bg: rgba(30, 41, 59, 0.4);
  --border-color: rgba(255, 255, 255, 0.15);
  --text-main: #f8fafc; /* Slate 50 */
  --text-sub: #cbd5e1; /* Slate 300 - Much lighter than before */
  --text-label: #94a3b8; /* Slate 400 */
}

.guest-order-container {
  position: relative;
  width: 100vw;
  height: 100vh;
  overflow-y: auto;
  overflow-x: hidden;
  background: radial-gradient(ellipse at bottom, #1e1b4b 0%, #020617 100%);
  font-family: 'Inter', sans-serif;
  color: var(--text-main);
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 20px;
}

#particle-canvas {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: 0;
  pointer-events: none;
}

.glow-spot {
  position: absolute;
  width: 600px;
  height: 600px;
  border-radius: 50%;
  filter: blur(120px);
  opacity: 0.4;
  z-index: 0;
  pointer-events: none;
  transition: transform 0.8s cubic-bezier(0.2, 0.8, 0.2, 1); /* Smooth avoidance */
}

.spot-1 {
  background: radial-gradient(circle, var(--primary), transparent 70%);
  top: -10%;
  left: -10%;
}

.spot-2 {
  background: radial-gradient(circle, var(--accent), transparent 70%);
  bottom: -10%;
  right: -10%;
}

.content-wrapper {
  position: relative;
  z-index: 10;
  width: 100%;
  max-width: 480px; /* Slightly wider */
  margin: 40px auto;
}

.glass-card {
  background: var(--card-bg);
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-top: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 24px;
  padding: 40px;
  box-shadow: 
    0 25px 50px -12px rgba(0, 0, 0, 0.5),
    0 0 0 1px rgba(255, 255, 255, 0.05) inset;
  opacity: 0;
  transform: translateY(20px);
  animation: slideUp 0.6s cubic-bezier(0.16, 1, 0.3, 1) forwards;
}

@keyframes slideUp {
  to { opacity: 1; transform: translateY(0); }
}

.card-header {
  text-align: center;
  margin-bottom: 36px;
  
  .logo-wrapper {
    display: inline-flex;
    padding: 12px;
    background: rgba(255, 255, 255, 0.05);
    border-radius: 20px;
    margin-bottom: 16px;
    border: 1px solid rgba(255, 255, 255, 0.1);
    box-shadow: 0 0 20px rgba(167, 139, 250, 0.2);
  }

  .logo-icon {
    width: 32px;
    height: 32px;
    color: var(--primary);
  }

  h1 {
    font-size: 30px;
    font-weight: 700;
    margin: 0 0 10px;
    background: linear-gradient(to right, #fff, #e2e8f0);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    letter-spacing: -0.5px;
  }

  p {
    font-size: 15px;
    color: var(--text-sub);
    white-space: nowrap; /* Prevent wrapping */
    overflow: hidden;
    text-overflow: ellipsis;
  }
}

/* Form Styles */
.form-item {
  margin-bottom: 24px;
}

.input-row {
  display: flex;
  gap: 16px;
  .form-item { flex: 1; margin-bottom: 0; }
}

.input-wrapper {
  position: relative;
  
  input {
    width: 100%;
    background: var(--input-bg);
    border: 1px solid var(--border-color);
    border-radius: 12px;
    padding: 18px 16px 6px; /* Adjusted padding */
    height: 56px;
    font-size: 15px;
    color: #fff;
    outline: none;
    transition: all 0.2s;
    
    &:focus {
      background: rgba(30, 41, 59, 0.8);
      border-color: var(--primary);
    }
    
    &:not(:placeholder-shown) + label,
    &:focus + label {
      transform: translateY(-12px) scale(0.85);
      color: var(--primary);
    }
  }

  label {
    position: absolute;
    left: 16px;
    top: 18px;
    color: var(--text-label); /* Lighter label color */
    font-size: 15px;
    pointer-events: none;
    transition: all 0.2s ease;
    transform-origin: left top;
  }
  
  .focus-border {
    position: absolute;
    bottom: 0;
    left: 50%;
    width: 0;
    height: 1px;
    background: var(--primary);
    transition: all 0.3s ease;
    transform: translateX(-50%);
  }
  
  input:focus ~ .focus-border {
    width: 100%;
    box-shadow: 0 0 10px var(--primary-glow);
  }
}

/* Custom Select */
.custom-select {
  position: relative;
  z-index: 20;
  
  .select-trigger {
    background: var(--input-bg);
    border: 1px solid var(--border-color);
    border-radius: 12px;
    padding: 0 16px;
    height: 56px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    cursor: pointer;
    transition: all 0.2s;
    
    &:hover {
      border-color: rgba(255, 255, 255, 0.3);
      background: rgba(30, 41, 59, 0.6);
    }
  }
  
  &.open .select-trigger {
    border-color: var(--primary);
    background: rgba(30, 41, 59, 0.8);
    .arrow-icon { transform: rotate(180deg); color: var(--primary); }
  }

  .trigger-content {
    display: flex;
    flex-direction: column;
    justify-content: center;
    width: 100%;
    
    .placeholder { color: var(--text-label); font-size: 15px; }
    .selected-text { 
      color: #fff; 
      font-weight: 500; 
      display: flex; 
      align-items: center; 
      gap: 8px;
      width: 100%;
    }
    .price-tag {
      font-size: 12px;
      background: rgba(34, 211, 238, 0.15);
      color: var(--accent);
      padding: 2px 6px;
      border-radius: 4px;
      margin-left: auto; /* Push to right */
    }
  }
  
  .arrow-icon {
    color: var(--text-label);
    transition: transform 0.3s;
    display: flex;
    margin-left: 12px;
  }

  .select-options {
    position: absolute;
    top: calc(100% + 8px);
    left: 0;
    width: 100%;
    background: #1e293b;
    border: 1px solid rgba(255, 255, 255, 0.1);
    border-radius: 12px;
    box-shadow: 0 20px 40px rgba(0,0,0,0.5);
    max-height: 260px;
    overflow-y: auto;
    z-index: 100;
    animation: fadeIn 0.2s ease;
    
    &::-webkit-scrollbar { width: 4px; }
    &::-webkit-scrollbar-thumb { background: #475569; border-radius: 2px; }
  }

  .option-item {
    padding: 14px 16px;
    display: flex;
    justify-content: space-between;
    align-items: center;
    cursor: pointer;
    border-bottom: 1px solid rgba(255, 255, 255, 0.03);
    transition: background 0.2s;
    
    &:hover {
      background: rgba(167, 139, 250, 0.1);
    }
    
    .platform-name { color: #e2e8f0; font-size: 14px; }
    .platform-price { color: var(--accent); font-weight: 600; font-size: 14px; }
  }
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(-10px); }
  to { opacity: 1; transform: translateY(0); }
}

/* Payment */
.payment-section {
  margin: 28px 0;
  .section-label {
    display: block;
    font-size: 13px;
    color: var(--text-sub); /* Lighter */
    margin-bottom: 12px;
    font-weight: 500;
  }
}

.payment-options {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.payment-option {
  position: relative;
  cursor: pointer;
  input { display: none; }
  
  .option-content {
    background: rgba(255, 255, 255, 0.03);
    border: 1px solid var(--border-color);
    border-radius: 12px;
    padding: 16px;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 10px;
    transition: all 0.3s;
    height: 60px;
    
    span { font-size: 15px; font-weight: 500; color: #e2e8f0; }
    
    .icon-box {
      width: 28px; height: 28px;
      display: flex; align-items: center; justify-content: center;
      border-radius: 6px;
      
      &.alipay { color: #1677ff; }
      &.wechat { color: #07c160; }
    }
  }
  
  &.active .option-content {
    background: rgba(167, 139, 250, 0.15);
    border-color: var(--primary);
    box-shadow: 0 0 0 1px var(--primary-glow);
  }
  
  &:hover .option-content {
    background: rgba(255, 255, 255, 0.08);
  }
}

/* Submit Button */
.submit-btn {
  position: relative;
  width: 100%;
  height: 56px;
  border: none;
  border-radius: 12px;
  background: linear-gradient(135deg, var(--primary), #8b5cf6);
  color: white;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  overflow: hidden;
  transition: all 0.3s;
  box-shadow: 0 4px 12px rgba(139, 92, 246, 0.3);
  
  .btn-content {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    transition: transform 0.3s;
  }
  
  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 8px 20px rgba(139, 92, 246, 0.4);
    .btn-content { transform: translateX(4px); }
  }
  
  &.loading {
    opacity: 0.8;
    pointer-events: none;
    .btn-content { opacity: 0; }
  }
}

.loader {
  position: absolute;
  top: 50%; left: 50%;
  transform: translate(-50%, -50%);
  width: 24px; height: 24px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin { to { transform: translate(-50%, -50%) rotate(360deg); } }

.card-footer {
  margin-top: 24px;
  text-align: center;
  font-size: 14px;
  color: var(--text-sub);
  
  .login-link {
    color: inherit;
    text-decoration: none;
    
    .highlight {
      color: var(--primary);
      font-weight: 500;
      margin-left: 4px;
      transition: color 0.2s;
    }
    
    &:hover .highlight {
      color: #c4b5fd;
      text-decoration: underline;
    }
  }
}

/* Mobile */
@media (max-width: 600px) {
  .glass-card { padding: 30px 20px; }
  .input-row { flex-direction: column; gap: 20px; }
  .input-row .form-item { margin-bottom: 0; }
}
</style>
