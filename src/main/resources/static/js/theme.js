const byId = id => document.getElementById(id);
const themes = {
  vintage: ['Edition 01 · Vintage', '今天想聊点什么护肤问题？', '从一份成分表、一个产品，或你的日常护肤步骤开始。', '/assets/stamp-vintage.webp'],
  rose: ['Edition 02 · Rose', '翻开你的护肤手帐', '把想查的成分、想试的产品和每天的护肤步骤记在这里。', '/assets/stamp-rose.webp'],
  pearl: ['Edition 03 · Pearl', '今天想了解什么？', '查成分、看产品、梳理你的日常护肤步骤。', '/assets/stamp-pearl.webp']
};
export function initTheme() {
  document.querySelectorAll('[data-set]').forEach(button => button.addEventListener('click', () => {
    const key = button.dataset.set;
    document.querySelector('.app').dataset.theme = key;
    document.querySelectorAll('[data-set]').forEach(item => item.setAttribute('aria-pressed', String(item === button)));
    const [edition, headline, description, stamp] = themes[key];
    byId('edition').textContent = edition; byId('headline').textContent = headline;
    byId('description').textContent = description; byId('stamp').src = stamp;
    localStorage.setItem('skincare-theme', key);
  }));
  const saved = localStorage.getItem('skincare-theme');
  document.querySelector(`[data-set="${themes[saved] ? saved : 'vintage'}"]`).click();
  let openingTimer;
  const stop = () => { clearTimeout(openingTimer); byId('opening').hidden = true; };
  const play = () => {
    stop(); if (matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    byId('opening').hidden = false; openingTimer = setTimeout(stop, 1650);
  };
  byId('skip').addEventListener('click', stop);
  byId('replay').addEventListener('click', play);
  play();
}
