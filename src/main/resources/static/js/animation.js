const frames = Array.from({length: 24}, (_, index) => `/assets/serum-24/frame_${String(index + 1).padStart(2, '0')}.webp`);
frames.forEach(src => { const image = new Image(); image.src = src; });
let timer = 0;
export function startThinking() {
  const layer = document.getElementById('thinking');
  const image = document.getElementById('tea-frame');
  const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)').matches;
  layer.hidden = false;
  document.getElementById('tea-caption').textContent = '正在整理资料和回答…';
  if (reducedMotion) { image.src = frames[0]; return; }
  const started = performance.now();
  let displayed = -1;
  function draw(now) {
    if (layer.hidden) return;
    // 一轮 24 帧约 3.4 秒；请求还没结束时循环播放。
    const index = Math.floor(((now - started) % 3400) / 3400 * frames.length);
    if (index !== displayed) { image.src = frames[index]; displayed = index; }
    timer = requestAnimationFrame(draw);
  }
  timer = requestAnimationFrame(draw);
}
export function stopThinking() {
  cancelAnimationFrame(timer);
  document.getElementById('thinking').hidden = true;
}
