import { marked } from 'marked';

const escapeHtml = (s) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

// raw HTML in markdown is shown as text, not rendered (as the previous markdown.js lib did)
marked.use({
  renderer: {
    html({ text }) {
      return escapeHtml(text);
    },
  },
});

window.marked = marked;
