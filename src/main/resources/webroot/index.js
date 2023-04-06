(function ($) {

    $(document).ready(function () {

        new Clipboard('#copyBtn');

        const $input = $('#sqlInput');
        const $output = $('#sqlOutput');

        const resetHeight = function () {
            const height = Math.round($(window).height() * 0.8) + 'px'
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

        const buildOptions = function () {
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
            return $.param(obj);
        };

        let timer = null;
        $input.on('input', function () {
            if (timer) {
                clearTimeout(timer);
            }
            timer = setTimeout(function () {
                const sql = $input.val();
                if (/^\s+$/.test(sql)) {
                    return;
                }
                $.ajax({
                    url: 'api/format?' + buildOptions(),
                    method: 'POST',
                    data: sql,
                    dataType: 'text',
                    success: function (data) {
                        $output.text(data);
                    },
                    error: function (xhr, status, error) {
                        $output.text(status + ': ' + error);
                    }
                });
            }, 200);
        });
    });
})(window.jQuery);