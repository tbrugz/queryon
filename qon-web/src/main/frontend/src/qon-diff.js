/*
Line diff for QueryOn pages: jsdiff (https://github.com/kpdecker/jsdiff) computes the diff, exposed through the
`difflib` / `diffview` globals previously provided by jsdifflib, so existing callers keep working.

diffview.buildView is a port of jsdifflib's diffview.js (with QueryOn's invertSides customization),
plus word-level highlighting inside replaced lines.

Based on jsdifflib v1.0. <http://github.com/cemerick/jsdifflib>

Copyright 2007 - 2011 Chas Emerick <cemerick@snowtide.com>. All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are
permitted provided that the following conditions are met:

   1. Redistributions of source code must retain the above copyright notice, this list of
      conditions and the following disclaimer.

   2. Redistributions in binary form must reproduce the above copyright notice, this list
      of conditions and the following disclaimer in the documentation and/or other materials
      provided with the distribution.

THIS SOFTWARE IS PROVIDED BY Chas Emerick ``AS IS'' AND ANY EXPRESS OR IMPLIED
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Chas Emerick OR
CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
import { diffArrays, diffWordsWithSpace } from 'diff';

// changed lines sharing less than this fraction of (non-whitespace) text are not word-highlighted
const WORD_DIFF_MIN_COMMON = 0.3;

function stringAsLines(str) {
  const lfpos = str.indexOf('\n');
  const crpos = str.indexOf('\r');
  const linebreak = ((lfpos > -1 && crpos > -1) || crpos < 0) ? '\n' : '\r';
  return str.split(linebreak).map(l => l.replace(/^[\n\r]*|[\n\r]*$/g, ''));
}

// Python difflib-style opcodes: [tag, i1, i2, j1, j2], tag in equal/replace/delete/insert
function getOpcodes(a, b) {
  const opcodes = [];
  let i = 0, j = 0, del = 0, ins = 0;
  const flush = () => {
    if (del || ins) {
      const tag = del && ins ? 'replace' : (del ? 'delete' : 'insert');
      opcodes.push([tag, i, i + del, j, j + ins]);
      i += del; j += ins; del = ins = 0;
    }
  };
  for (const part of diffArrays(a, b)) {
    const n = part.value.length;
    if (part.removed) { del += n; }
    else if (part.added) { ins += n; }
    else {
      flush();
      opcodes.push(['equal', i, i + n, j, j + n]);
      i += n; j += n;
    }
  }
  flush();
  return opcodes;
}

function SequenceMatcher(a, b) {
  this.a = a;
  this.b = b;
  this.opcodes = null;
  this.get_opcodes = function () {
    if (!this.opcodes) this.opcodes = getOpcodes(this.a, this.b);
    return this.opcodes;
  };
}

const expandTabs = s => s.replace(/\t/g, '\u00a0\u00a0\u00a0\u00a0');

// word diff between an old and a new line; null if lines are too different to be worth highlighting
function wordDiff(oldLine, newLine) {
  if (oldLine === newLine) return null;
  const parts = diffWordsWithSpace(oldLine, newLine);
  const nonWs = s => s.replace(/\s+/g, '').length;
  const common = parts.filter(p => !p.added && !p.removed).reduce((n, p) => n + nonWs(p.value), 0);
  const longest = Math.max(nonWs(oldLine), nonWs(newLine));
  if (longest === 0 || common / longest < WORD_DIFF_MIN_COMMON) return null;
  return parts;
}

// fills a cell with one side ('old' or 'new') of a word diff
function appendWordDiff(cell, parts, side) {
  for (const p of parts) {
    if (side === 'old' ? p.added : p.removed) continue;
    const text = document.createTextNode(expandTabs(p.value));
    if (p.added || p.removed) {
      const mark = document.createElement(p.added ? 'ins' : 'del');
      mark.className = 'wdiff';
      mark.appendChild(text);
      cell.appendChild(mark);
    } else {
      cell.appendChild(text);
    }
  }
}

/**
 * Builds and returns a visual diff view (a <table> element). Params:
 * - baseTextLines, newTextLines: arrays of lines (see difflib.stringAsLines)
 * - opcodes: from difflib.SequenceMatcher(base, new).get_opcodes()
 * - baseTextName, newTextName: titles (default "Base Text" / "New Text")
 * - contextSize: number of context lines shown around differences; all lines if not set
 * - viewType: 0 = side-by-side (default), 1 = inline
 * - invertSides: 0 = base on the left (default), 1 = base on the right
 */
function buildView(params) {
  const baseTextLines = params.baseTextLines;
  const newTextLines = params.newTextLines;
  const opcodes = params.opcodes;
  const baseTextName = params.baseTextName ? params.baseTextName : 'Base Text';
  const newTextName = params.newTextName ? params.newTextName : 'New Text';
  const contextSize = params.contextSize;
  const inline = (params.viewType == 0 || params.viewType == 1) ? params.viewType : 0;
  const invertSides = (params.invertSides == 0 || params.invertSides == 1) ? params.invertSides : 0;

  if (baseTextLines == null) throw 'Cannot build diff view; baseTextLines is not defined.';
  if (newTextLines == null) throw 'Cannot build diff view; newTextLines is not defined.';
  if (!opcodes) throw 'Cannot build diff view; opcodes is not defined.';

  const celt = (name, clazz) => {
    const e = document.createElement(name);
    e.className = clazz;
    return e;
  };
  const telt = (name, text) => {
    const e = document.createElement(name);
    e.appendChild(document.createTextNode(text));
    return e;
  };
  const ctelt = (name, clazz, text) => {
    const e = celt(name, clazz);
    e.appendChild(document.createTextNode(text));
    return e;
  };
  // text cell; with word diff parts when available
  const textCell = (change, line, parts, side) => {
    if (!parts) return ctelt('td', change, expandTabs(line));
    const td = celt('td', change);
    appendWordDiff(td, parts, side);
    return td;
  };

  const leftTextName = invertSides ? newTextName : baseTextName;
  const rightTextName = invertSides ? baseTextName : newTextName;
  const leftClass = invertSides ? 'new' : 'base';
  const rightClass = invertSides ? 'base' : 'new';

  const thead = document.createElement('thead');
  let node = document.createElement('tr');
  thead.appendChild(node);
  if (inline) {
    node.appendChild(document.createElement('th'));
    node.appendChild(document.createElement('th'));
    node.appendChild(ctelt('th', 'texttitle', leftTextName + ' vs. ' + rightTextName));
  } else {
    node.appendChild(document.createElement('th'));
    node.appendChild(ctelt('th', 'texttitle ' + leftClass, leftTextName));
    node.appendChild(document.createElement('th'));
    node.appendChild(ctelt('th', 'texttitle ' + rightClass, rightTextName));
  }

  const rows = [];

  // side-by-side: line number + text cells for one side, or empty cells past the end of the range
  function addCells(row, tidx, tend, textLines, change, parts, side) {
    if (tidx < tend) {
      row.appendChild(telt('th', (tidx + 1).toString()));
      row.appendChild(textCell(change, textLines[tidx], parts, side));
      return tidx + 1;
    }
    row.appendChild(document.createElement('th'));
    row.appendChild(celt('td', 'empty'));
    return tidx;
  }

  function addCellsInline(row, tidx, tidx2, textLines, change, parts, side) {
    const num1 = telt('th', tidx == null ? '' : (tidx + 1).toString());
    const num2 = telt('th', tidx2 == null ? '' : (tidx2 + 1).toString());
    if (invertSides) {
      row.appendChild(num2);
      row.appendChild(num1);
    } else {
      row.appendChild(num1);
      row.appendChild(num2);
    }
    row.appendChild(textCell(change, textLines[tidx != null ? tidx : tidx2], parts, side));
  }

  for (let idx = 0; idx < opcodes.length; idx++) {
    const [change, b0, be, n0, ne] = opcodes[idx];
    let b = b0, n = n0;
    const rowcnt = Math.max(be - b, ne - n);
    const toprows = [];
    const botrows = [];
    for (let i = 0; i < rowcnt; i++) {
      // jump ahead if we've already provided leading context or if this is the first range
      if (contextSize && opcodes.length > 1 && ((idx > 0 && i == contextSize) || (idx == 0 && i == 0)) && change == 'equal') {
        const jump = rowcnt - ((idx == 0 ? 1 : 2) * contextSize);
        if (jump > 1) {
          toprows.push(node = document.createElement('tr'));
          b += jump;
          n += jump;
          i += jump - 1;
          node.appendChild(telt('th', '...'));
          if (!inline) node.appendChild(ctelt('td', 'skip', ''));
          node.appendChild(telt('th', '...'));
          node.appendChild(ctelt('td', 'skip', ''));
          // skip last lines if they're all equal
          if (idx + 1 == opcodes.length) break;
          continue;
        }
      }

      // in replaced blocks, the i-th old line is word-diffed against the i-th new line
      const parts = (change == 'replace' && b < be && n < ne) ? wordDiff(baseTextLines[b], newTextLines[n]) : null;

      toprows.push(node = document.createElement('tr'));
      if (inline) {
        if (change == 'insert') {
          addCellsInline(node, null, n++, newTextLines, change);
        } else if (change == 'replace') {
          const node2 = document.createElement('tr');
          botrows.push(node2);
          if (b < be) addCellsInline(node, b++, null, baseTextLines, 'delete', parts, 'old');
          if (n < ne) addCellsInline(node2, null, n++, newTextLines, 'insert', parts, 'new');
        } else if (change == 'delete') {
          addCellsInline(node, b++, null, baseTextLines, change);
        } else {
          addCellsInline(node, b++, n++, baseTextLines, change);
        }
      } else if (invertSides) {
        n = addCells(node, n, ne, newTextLines, change, parts, 'new');
        b = addCells(node, b, be, baseTextLines, change, parts, 'old');
      } else {
        b = addCells(node, b, be, baseTextLines, change, parts, 'old');
        n = addCells(node, n, ne, newTextLines, change, parts, 'new');
      }
    }
    rows.push(...toprows, ...botrows);
  }

  const tbody = document.createElement('tbody');
  for (const row of rows) tbody.appendChild(row);

  const table = celt('table', 'diff' + (inline ? ' inlinediff' : ''));
  table.appendChild(thead);
  table.appendChild(tbody);
  return table;
}

window.difflib = { stringAsLines, SequenceMatcher };
window.diffview = { buildView };
