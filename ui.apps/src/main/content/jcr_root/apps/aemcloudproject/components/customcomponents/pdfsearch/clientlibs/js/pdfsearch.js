(function () {
  async function csrf() {
    const r = await fetch('/libs/granite/csrf/token.json', { credentials: 'same-origin' });
    const j = await r.json();
    return j.token;
  }

  function $(root, sel) { return root.querySelector(sel); }
  function html(el, s) { el.innerHTML = s; }

  async function uploadToAem(file, token) {
    const fd = new FormData();
    fd.append("file", file);
    const r = await fetch("/bin/pdfsearch/upload", {
      method: "POST",
      headers: { "CSRF-Token": token },
      body: fd,
      credentials: "same-origin"
    });
    if (!r.ok) throw new Error(await r.text());
    return r.json(); // { doc_id, chunks }
  }

  async function searchViaAem(q, topK, docId) {
    const params = new URLSearchParams({ q, top_k: String(topK) });
    if (docId) params.set("doc_id", docId);
    const r = await fetch(`/bin/pdfsearch/search?${params.toString()}`, { credentials: "same-origin" });
    if (!r.ok) throw new Error(await r.text());
    return r.json(); // array of hits
  }

  function renderResults(node, hits) {
    if (!hits || !hits.length) { html(node, '<div class="ps-empty">No results.</div>'); return; }
    hits.sort((a,b) => (b.score||0) - (a.score||0)); // ensure highest score first
    html(node, hits.map(h => `
      <div class="ps-hit">
        <div class="ps-meta">Score: ${(h.score||0).toFixed(2)} | Chunk #${h.chunk_index}</div>
        <div class="ps-content">${(h.content||'').replace(/</g,'&lt;')}</div>
      </div>
    `).join(''));
  }

  document.addEventListener("DOMContentLoaded", () => {
    document.querySelectorAll(".pdfsearch").forEach(root => {
      const file = $(root, ".ps-file");
      const uploadBtn = $(root, ".ps-upload");
      const status = $(root, ".ps-status");
      const query = $(root, ".ps-query");
      const goBtn = $(root, ".ps-search");
      const docIdField = $(root, ".ps-docid");
      const results = $(root, ".ps-results");
      const topK = Number(root.dataset.topk || 5);

      uploadBtn.addEventListener("click", async () => {
        try {
          if (!file.files || !file.files[0]) { status.textContent = "Choose a PDF first."; return; }
          status.textContent = "Uploading…";
          const token = await csrf();
          const res = await uploadToAem(file.files[0], token);
          docIdField.value = res.doc_id;
          status.textContent = `Uploaded ✓ chunks: ${res.chunks}`;
        } catch (e) {
          status.textContent = `Upload failed: ${e.message}`;
        }
      });

      async function runSearch() {
        const q = (query.value || "").trim();
        if (!q) return;
        $(root, ".ps-hint").textContent = "Searching…";
        try {
          const hits = await searchViaAem(q, topK, docIdField.value || null);
          renderResults(results, hits);
          $(root, ".ps-hint").textContent = `Found ${hits.length}`;
        } catch (e) {
          $(root, ".ps-hint").textContent = `Search failed: ${e.message}`;
        }
      }
      goBtn.addEventListener("click", runSearch);
      query.addEventListener("keydown", e => { if (e.key === "Enter") runSearch(); });
    });
  });
})();