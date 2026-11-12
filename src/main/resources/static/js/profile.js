import {request, put, profileUrl} from './api.js';
const byId = id => document.getElementById(id);
let profile = null;
export function currentProfile() { return profile; }
export async function loadProfile() {
  const userId = byId('user-id').value.trim();
  if (!userId) return;
  try {
    profile = await request(profileUrl(userId));
    byId('skin-summary').textContent = profile.skinType || '已登记';
    byId('profile-status').textContent = '';
  } catch (error) {
    profile = null;
    byId('skin-summary').textContent = '点击填写肤质';
    if (!error.message.includes('404')) byId('profile-status').textContent = error.message;
  }
}
export function initProfile() {
  const dialog = byId('dialog');
  const form = byId('profile-form');
  byId('profile').addEventListener('click', async () => {
    await loadProfile();
    form.elements.skin.value = profile?.skinType || '';
    form.elements.allergens.value = profile?.allergens?.join('、') || '';
    form.elements.confirmed.checked = false;
    dialog.showModal();
  });
  byId('close').addEventListener('click', () => dialog.close());
  form.addEventListener('submit', async event => {
    event.preventDefault();
    const userId = byId('user-id').value.trim();
    if (!userId) { byId('profile-status').textContent = '请先填写用户 ID。'; return; }
    const allergens = form.elements.allergens.value.split(/[,，、]/).map(value => value.trim()).filter(Boolean);
    try {
      profile = await put(profileUrl(userId), {skinType: form.elements.skin.value.trim(), allergens, confirmed: form.elements.confirmed.checked});
      byId('skin-summary').textContent = profile.skinType || '已登记';
      byId('profile-status').textContent = '已保存确认画像。';
      dialog.close();
    } catch (error) { byId('profile-status').textContent = error.message; }
  });
  byId('user-id').addEventListener('change', loadProfile);
  loadProfile();
}
