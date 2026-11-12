import {post, request} from './api.js';
const byId = id => document.getElementById(id);
const value = id => byId(id).value.trim();
function setStatus(text) { byId('tool-status').textContent = text; }
function link(url, label) {
  const element = document.createElement('a'); element.textContent = label;
  try { const parsed = new URL(url); if (parsed.protocol === 'https:') { element.href = parsed.href; element.target = '_blank'; element.rel = 'noopener noreferrer'; } }
  catch { /* 来源无效时只显示文字。 */ }
  return element;
}
async function saved() {
  const container = byId('saved-list'); container.replaceChildren();
  try {
    const products = await request(`/api/products/saved/${encodeURIComponent(value('user-id'))}`);
    if (!products.length) { container.textContent = '当前用户还没有保存的成分表。'; return; }
    for (const product of products) {
      const row = document.createElement('div'); row.className = 'saved-row';
      const title = document.createElement('span'); title.textContent = `${product.name}（${product.sourceLabel || '已保存'}）`;
      row.append(title);
      for (const [label, nameId, listId] of [['载入 A','product-a-name','product-a'], ['载入 B','product-b-name','product-b']]) {
        const button = document.createElement('button'); button.type = 'button'; button.textContent = label;
        button.addEventListener('click', () => { byId(nameId).value = product.name; byId(listId).value = product.ingredients; byId('ingredients-confirmed').checked = false; setStatus(`已载入${product.name}，请核对包装。`); });
        row.append(button);
      }
      const remove = document.createElement('button'); remove.type = 'button'; remove.textContent = '删除';
      remove.addEventListener('click', async () => {
        try { await request(`/api/products/saved/${encodeURIComponent(value('user-id'))}?productName=${encodeURIComponent(product.name)}`, {method:'DELETE'}); await saved(); }
        catch (error) { setStatus(error.message); }
      }); row.append(remove); container.append(row);
    }
  } catch (error) { setStatus(error.message); }
}
export function initTools() {
  byId('extract-web').addEventListener('click', async () => {
    const button = byId('extract-web'); button.disabled = true; setStatus('正在提取候选成分…');
    try {
      const data = await post('/api/products/web-ingredients', {productName:value('product-a-name'), market:value('product-market'), version:value('product-version'), url:value('product-url')});
      byId('product-a').value = data.ingredients.join('、'); byId('ingredients-confirmed').checked = false;
      byId('tool-status').replaceChildren(document.createTextNode(`已提取 ${data.ingredients.length} 项候选成分；来源未核实，请核对包装。 `), link(data.sourceUrl,'查看网页'));
    } catch (error) { setStatus(error.message); }
    finally { button.disabled = false; }
  });
  byId('load-saved').addEventListener('click', saved);
  byId('knowledge-form').addEventListener('submit', async event => {
    event.preventDefault(); const container = byId('knowledge-results'); container.replaceChildren();
    if (!value('knowledge-query')) return;
    try {
      const data = await post('/api/knowledge/search', {query:value('knowledge-query'),topK:3});
      if (!data.matches?.length) { container.textContent = '未检索到有依据的片段。'; return; }
      for (const hit of data.matches) {
        const article = document.createElement('article');
        const excerpt = document.createElement('p'); excerpt.textContent = hit.chunk?.text || '';
        article.append(excerpt);
        if (hit.chunk?.source) article.append(link(hit.chunk.source.url,[hit.chunk.source.publisher,hit.chunk.source.title].filter(Boolean).join(' · ')));
        container.append(article);
      }
    } catch (error) { container.textContent = error.message; }
  });
}
