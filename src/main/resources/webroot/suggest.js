(function ($) {

    //https://github.com/ClickHouse/ClickHouse/blob/master/utils/antlr/ClickHouseLexer.g4
    const keywords = ['AFTER', 'ALIAS', 'ALL', 'ALTER', 'AND', 'ANTI', 'ANY', 'ARRAY', 'AS', 'ASCENDING', 'ASOF', 'AST', 'ASYNC', 'ATTACH', 'BETWEEN', 'BOTH', 'BY', 'CASE', 'CAST', 'CHECK', 'CLEAR', 'CLUSTER', 'CODEC', 'COLLATE', 'COLUMN', 'COMMENT', 'CONSTRAINT', 'CREATE', 'CROSS', 'CUBE', 'CURRENT', 'DATABASE', 'DATABASES', 'DATE', 'DEDUPLICATE', 'DEFAULT', 'DELAY', 'DELETE', 'DESCRIBE', 'DESC', 'DESCENDING', 'DETACH', 'DICTIONARIES', 'DICTIONARY', 'DISK', 'DISTINCT', 'DISTRIBUTED', 'DROP', 'ELSE', 'END', 'ENGINE', 'EVENTS', 'EXISTS', 'EXPLAIN', 'EXPRESSION', 'EXTRACT', 'FETCHES', 'FINAL', 'FIRST', 'FLUSH', 'FOR', 'FOLLOWING', 'FOR', 'FORMAT', 'FREEZE', 'FROM', 'FULL', 'FUNCTION', 'GLOBAL', 'GRANULARITY', 'GROUP', 'HAVING', 'HIERARCHICAL', 'ID', 'IF', 'ILIKE', 'IN', 'INDEX', 'INJECTIVE', 'INNER', 'INSERT', 'INTERVAL', 'INTO', 'IS', 'IS', 'OBJECT', 'ID', 'JOIN', 'JSON', 'FALSE', 'JSON', 'TRUE', 'KEY', 'KILL', 'LAST', 'LAYOUT', 'LEADING', 'LEFT', 'LIFETIME', 'LIKE', 'LIMIT', 'LIVE', 'LOCAL', 'LOGS', 'MATERIALIZE', 'MATERIALIZED', 'MAX', 'MERGES', 'MIN', 'MODIFY', 'MOVE', 'MUTATION', 'NO', 'NOT', 'NULLS', 'OFFSET', 'ON', 'OPTIMIZE', 'OR', 'ORDER', 'OUTER', 'OUTFILE', 'OVER', 'PARTITION', 'POPULATE', 'PRECEDING', 'PREWHERE', 'PRIMARY', 'RANGE', 'RELOAD', 'REMOVE', 'RENAME', 'REPLACE', 'REPLICA', 'REPLICATED', 'RIGHT', 'ROLLUP', 'ROW', 'ROWS', 'SAMPLE', 'SELECT', 'SEMI', 'SENDS', 'SET', 'SETTINGS', 'SHOW', 'SOURCE', 'START', 'STOP', 'SUBSTRING', 'SYNC', 'SYNTAX', 'SYSTEM', 'TABLE', 'TABLES', 'TEMPORARY', 'TEST', 'THEN', 'TIES', 'TIMEOUT', 'TIMESTAMP', 'TOTALS', 'TRAILING', 'TRIM', 'TRUNCATE', 'TO', 'TOP', 'TTL', 'TYPE', 'UNBOUNDED', 'UNION', 'UPDATE', 'USE', 'USING', 'UUID', 'VALUES', 'VIEW', 'VOLUME', 'WATCH', 'WHEN', 'WHERE', 'WINDOW', 'WITH'];
    const mapFunc = function (s) {
        return {
            text: s, value: s.slice(1, s.length)
        };
    };
    const options = {};
    const registerKeywords = function (keyword) {
        const key = keyword.slice(0, 1);
        if (!Object.hasOwn(options, key)) {
            options[key] = {
                data: [keyword], map: mapFunc
            };
        } else {
            options[key].data.push(keyword);
        }
    }
    for (let keyword of keywords) {
        registerKeywords(keyword);
        registerKeywords(keyword.toLowerCase());
    }

    $(document).ready(function () {
        $('#sqlInput').suggest(options);
    });
})(window.jQuery)