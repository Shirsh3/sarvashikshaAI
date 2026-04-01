/**
 * Reads grade from ?grade= URL (set by Learning section links) or localStorage sarva.prepareGrade.
 * "Open" / empty = no grade — clears hidden prepareGrade and hides banners.
 */
(function () {
    var K = 'sarva.prepareGrade';

    function gradeFromUrl() {
        try {
            var q = new URLSearchParams(window.location.search).get('grade');
            return q != null && String(q).trim() !== '' ? String(q).trim() : null;
        } catch (e) {
            return null;
        }
    }

    function gradeFromStorage() {
        try {
            var v = localStorage.getItem(K);
            return v != null && String(v).trim() !== '' ? String(v).trim() : null;
        } catch (e) {
            return null;
        }
    }

    /** Current grade for this screen: URL wins, then storage. */
    window.getPrepareGradeContext = function () {
        return gradeFromUrl() || gradeFromStorage();
    };

    window.syncPrepareGradeToFormAndBanners = function () {
        var g = window.getPrepareGradeContext();
        var inp = document.getElementById('inputPrepareGrade');
        if (inp) inp.value = g ? g : '';

        var embedHint = document.getElementById('prepareGradeEmbedHint');
        if (embedHint) {
            if (g) {
                embedHint.style.display = '';
                embedHint.textContent = 'Using grade ' + g + '.';
            } else {
                embedHint.style.display = 'none';
                embedHint.textContent = '';
            }
        }

        var banners = document.querySelectorAll('[data-prepare-grade-banner]');
        banners.forEach(function (el) {
            if (g) {
                el.style.display = '';
                el.textContent = 'Grade ' + g + ' — reading and quizzes will match this level.';
            } else {
                el.style.display = 'none';
            }
        });
    };

    // Landing with ?grade= keeps dashboard and explain in sync
    try {
        var fromUrl = gradeFromUrl();
        if (fromUrl) localStorage.setItem(K, fromUrl);
    } catch (e) {}

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function () {
            window.syncPrepareGradeToFormAndBanners();
        });
    } else {
        window.syncPrepareGradeToFormAndBanners();
    }
})();
