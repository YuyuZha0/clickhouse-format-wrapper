(function ($) {

    const $input = $('#sqlInput');
    const $output = $('#sqlOutput');

    const resetHeight = function () {
        const height = Math.round($(window).height() * 0.75) + 'px'
        $input.css('height', height);
        $output.css('height', height);
    };

    resetHeight();
    $(window).on('resize', resetHeight);

    const $hilite = $('#hiliteCheck');
    const $oneline = $('#onelineCheck');
    const $multiquery = $('#multiqueryCheck');
    const $backslash = $('#backslashCheck');
    const $allowSettingsAfterFormatInInsert = $('#allowSettingsAfterFormatInInsertCheck');
    const $obfuscate = $('#obfuscateCheck');

    const $seed = $('#seedInput');
    const $maxQuerySize = $('#maxQuerySizeInput');
    const $maxParserDepth = $('#maxParserDepthInput');
    const $progressBar = $('#progressBar');

    const $autoFormat = $('#autoFormatCheck');
    const $formatBtn = $('#formatBtn');

    const updateProgressBar = function (rate) {
        const nPercent = Math.min(Math.round(rate * 100), 100);
        $progressBar.css('width', `${nPercent}%`);
        $progressBar.attr('aria-valuenow', nPercent);
    };
    const initProgressBar = function () {
        $progressBar.addClass('progress-bar-striped progress-bar-animated');
        updateProgressBar(0);
    };
    const completeProgressBar = function () {
        $progressBar.removeClass('progress-bar-striped progress-bar-animated');
        updateProgressBar(1);
    };
    const xhrFactory = function () {
        const xhr = new window.XMLHttpRequest();
        xhr.upload.onprogress = function (evt) {
            if (evt.lengthComputable) {
                const percentComplete = evt.loaded / evt.total;
                updateProgressBar(percentComplete / 2);
            } else {
                updateProgressBar(0.4);
            }
        };

        xhr.addEventListener("progress", function (evt) {
            if (evt.lengthComputable) {
                const percentComplete = evt.loaded / evt.total;
                updateProgressBar(0.5 + percentComplete / 2);
            } else {
                updateProgressBar(0.9)
            }
        }, false);

        return xhr;
    }


    $(document).ready(function () {
        const getOptions = function () {
            const obj = {
                hilite: $hilite.prop('checked'),
                oneline: $oneline.prop('checked'),
                multiquery: $multiquery.prop('checked'),
                backslash: $backslash.prop('checked'),
                allowSettingsAfterFormatInInsert: $allowSettingsAfterFormatInInsert.prop('checked'),
                obfuscate: $obfuscate.prop('checked'),
                seed: $seed.val(),
                maxQuerySize: $maxQuerySize.val(),
                maxParserDepth: $maxParserDepth.val()
            };
            for (let key in obj) {
                if (obj[key] === '') {
                    delete obj[key];
                }
            }
            return obj;
        };

        const createHighlighter = function () {
            if (window.AnsiUp) {
                const ansiUp = new AnsiUp();
                return function (text) {
                    return ansiUp.ansi_to_html(text);
                };
            } else {
                console.warn('AnsiUp not available!');
                return function (text) {
                    return text;
                };
            }
        };


        const highlighter = createHighlighter();
        const formatRequest = function () {
            const sql = $input.val();
            if (/^\s*$/.test(sql)) {
                return;
            }
            $output.empty();
            initProgressBar();
            const options = getOptions();
            $.ajax({
                xhr: xhrFactory,
                url: 'api/format?' + $.param(options),
                method: 'POST',
                contentType: "text/plain; charset=utf-8",
                data: sql,
                dataType: 'text'
            }).done(function (data) {
                if (typeof data === 'string' || data instanceof String) {
                    if (options.hilite) {
                        $output.html(highlighter(data));
                    } else {
                        $output.html(`<code>${data}</code>`);
                    }
                }
            }).fail(function (xhr, status, error) {
                $output.html(`<p class="text-danger">${xhr.responseText}</p>`);
            }).always(function () {
                completeProgressBar();
                window.localStorage.setItem('sql', sql);
            });
        }

        let timer = null;
        $input.on('input', function () {
            if (timer) {
                clearTimeout(timer);
                timer = null;
            }
            if (!$autoFormat.prop('checked')) {
                return;
            }
            timer = setTimeout(formatRequest, 200);
        });
        // $('#mainForm input[type="checkbox"]').on('change', formatRequest);
        $formatBtn.on('click', formatRequest);

        const initClipboard = function () {
            if (window.Clipboard) {
                new Clipboard('#copyBtn');
            } else {
                console.warn('Clipboard not available!');
            }
        };
        initClipboard();

        const fillInput = function () {
            const sql = window.localStorage.getItem('sql');
            if (sql) {
                $input.val(sql);
            }
        };
        fillInput();

    });
})(window.jQuery);