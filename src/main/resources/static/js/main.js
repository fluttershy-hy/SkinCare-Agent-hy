import {post} from './api.js';
import {startThinking, stopThinking} from './animation.js';
import {initProfile, currentProfile, loadProfile} from './profile.js';
import {initTheme} from './theme.js';
import {initTools} from './tools.js';
const byId = id => document.getElementById(id);
const value = id => byId(id).value.trim();
let sessionId = crypto.randomUUID();
let pending = false;
const shortcuts = [
  ['查成分', '读一份产品成分表', '帮我看看这款产品的成分表'],
  ['看适配', '结合我的肤质档案', '这款产品适合我的肤质吗？'],
  ['梳理步骤', '整理早晚护肤流程', '帮我整理早晚护肤步骤']
];
function updateSession() { byId('session-status').textContent = `本次会话：${sessionId.slice(0, 8)}`; }
function reset() {
  if (pending) return;
  sessionId = crypto.randomUUID(); updateSession();
  byId('messages').replaceChildren(); byId('messages').hidden = true;
  byId('welcome').hidden = false; byId('prompt').value = '';
}
function scroll() { byId('feed').scrollTop = byId('feed').scrollHeight; }
function message(label, text, isUser = false, isError = false) {
  byId('welcome').hidden = true; byId('messages').hidden = false;
  const row = document.createElement('div');
  row.className = `message${isUser ? ' user' : ''}${isError ? ' error' : ''}`;
  const name = document.createElement('strong'); name.textContent = label;
  const body = document.createElement('span'); body.textContent = text || '服务没有返回内容。';
  row.append(name, body); byId('messages').append(row); scroll();
  return row;
}
function appendSources(sources = []) {
  if (!sources.length) return;
  const panel = document.createElement('details'); panel.className = 'sources'; panel.open = true;
  const heading = document.createElement('summary'); heading.textContent = `查看依据（${sources.length}）`;
  panel.append(heading);
  const list = document.createElement('ul');
  for (const source of sources) {
    const item = document.createElement('li');
    const title = [source.publisher, source.title, source.section].filter(Boolean).join(' · ') || '来源';
    let url;
    try { url = new URL(source.url); } catch { /* 只显示标题。 */ }
    if (url?.protocol === 'https:') {
      const link = document.createElement('a'); link.href = url.href;
      link.target = '_blank'; link.rel = 'noopener noreferrer'; link.textContent = title;
      item.append(link);
    } else item.textContent = title;
    if (source.verifiedOn) item.append(document.createTextNode(`（查阅于 ${source.verifiedOn}）`));
    list.append(item);
  }
  panel.append(list); byId('messages').append(panel); scroll();
}
function context() {
  const allergies = value('ingredient-allergies').split(/[,，、]/).map(s => s.trim()).filter(Boolean);
  const sensitive = value('sensitive') === 'yes' ? true : value('sensitive') === 'no' ? false : null;
  return {userId: value('user-id'), concern: value('concern'), bodyArea: value('body-area'),
    currentState: value('current-state'), sensitive, confirmedIngredientAllergies: allergies,
    allergiesConfirmed: byId('allergies-confirmed').checked};
}
async function send(input) {
  const text = input.trim(); if (!text || pending) return;
  if (!value('user-id')) { message('提示', '请先填写左侧用户 ID。', false, true); return; }
  const productA = value('product-a'), productB = value('product-b');
  if (productB && !productA) { message('提示', '请先填写产品 A 的成分表。', false, true); return; }
  message('你', text, true);
  byId('prompt').value = '';
  pending = true; byId('composer').querySelector('.send').disabled = true;
  byId('new').disabled = true; startThinking();
  try {
    const common = context();
    let data;
    if (productA) {
      // 成分表存在时沿用项目的 Java 确定性过敏匹配和成分分析接口。
      data = await post('/api/ingredients/analyze', {...common,
        first: {name: value('product-a-name') || '产品 A', ingredients: productA},
        second: productB ? {name: value('product-b-name') || '产品 B', ingredients: productB} : null,
        skinType: currentProfile()?.skinType || '', question: text,
        productIngredientsConfirmed: byId('ingredients-confirmed').checked});
    } else {
      data = await post('/api/chat', {...common, sessionId, message: text,
        productName: value('product-a-name') || null});
    }
    stopThinking();
    message('SkinCare Agent', data.answer);
    appendSources(data.sources);
    if (data.assessment?.decision === 'AVOID_CONFIRMED_MATCH' && !data.answer?.includes('过敏')) {
      message('已确认过敏成分提示', data.assessment.reason);
    }
  } catch (error) { stopThinking(); message('请求未完成', error.message, false, true); }
  finally {
    pending = false; byId('composer').querySelector('.send').disabled = false;
    byId('new').disabled = false; byId('prompt').focus();
  }
}
function initShortcuts() {
  for (const [label, hint, prompt] of shortcuts) {
    const button = document.createElement('button');
    const small = document.createElement('small'); small.textContent = hint;
    button.append(label, small); button.addEventListener('click', () => { byId('prompt').value = prompt; byId('prompt').focus(); });
    byId('prompts').append(button);
    const nav = document.createElement('button'); nav.textContent = hint;
    nav.addEventListener('click', () => { byId('prompt').value = prompt; byId('prompt').focus(); });
    byId('history').append(nav);
  }
}
updateSession(); initTheme(); initProfile(); initShortcuts(); initTools();
byId('new').addEventListener('click', () => { reset(); byId('prompt').focus(); });
byId('user-id').addEventListener('change', () => { reset(); loadProfile(); });
byId('composer').addEventListener('submit', event => { event.preventDefault(); send(value('prompt')); });
