(function ($) {

    $(document).ready(function () {

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

        const ansiUp = new AnsiUp();
        const formatRequest = function () {
            const sql = $input.val();
            if (/^\s*$/.test(sql)) {
                return;
            }
            $output.empty();
            const options = getOptions();
            $.ajax({
                url: 'api/format?' + $.param(options),
                method: 'POST',
                data: sql,
                dataType: 'text',
                success: function (data) {
                    if (typeof data === 'string' || data instanceof String) {
                        if (options.hilite) {
                            $output.html(ansiUp.ansi_to_html(data));
                        } else {
                            $output.html(`<pre><code>${data}</code></pre>`);
                        }
                    }
                },
                error: function (xhr, status, error) {
                    $output.html(`<p class="text-danger">${status}: ${xhr.responseText}</p>`);
                }
            });
        }

        let timer = null;
        $input.on('input', function () {
            if (timer) {
                clearTimeout(timer);
            }
            timer = setTimeout(formatRequest, 200);
        });
        new Clipboard('#copyBtn');
    });
})(window.jQuery);