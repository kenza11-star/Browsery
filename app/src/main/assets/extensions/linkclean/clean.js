// Pembersih Tautan Browsery: menghapus parameter pelacak umum dari atribut href tautan di halaman.
// Tidak mengirim data apa pun ke luar halaman dan tidak membaca isi formulir.
(function () {
  "use strict";
  var TRACKING = /^(utm_[a-z0-9_]+|fbclid|gclid|dclid|msclkid|mc_eid|igshid|yclid|_hsenc|_hsmi)$/i;

  function clean(a) {
    try {
      var u = new URL(a.href);
      var changed = false;
      Array.from(u.searchParams.keys()).forEach(function (k) {
        if (TRACKING.test(k)) {
          u.searchParams.delete(k);
          changed = true;
        }
      });
      if (changed) {
        a.href = u.toString();
      }
    } catch (e) {
      // href tidak valid: biarkan.
    }
  }

  function scan(root) {
    if (root.matches && root.matches("a[href]")) {
      clean(root);
    }
    if (root.querySelectorAll) {
      root.querySelectorAll("a[href]").forEach(clean);
    }
  }

  scan(document);

  new MutationObserver(function (mutations) {
    mutations.forEach(function (m) {
      m.addedNodes.forEach(function (n) {
        if (n.nodeType === 1) {
          scan(n);
        }
      });
    });
  }).observe(document.documentElement, { childList: true, subtree: true });
})();
