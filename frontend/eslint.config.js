import js from '@eslint/js'
import vue from 'eslint-plugin-vue'
import globals from 'globals'
import prettier from 'eslint-config-prettier'
import noInlineUiCopy from './eslint-rules/no-inline-ui-copy.js'

export default [
    {
        ignores: [
            'dist/**',
            'node_modules/**',
            'coverage/**',
            'playwright-report/**',
            'test-results/**',
        ],
    },
    js.configs.recommended,
    ...vue.configs['flat/recommended'],
    {
        files: ['src/**/*.{js,vue}', 'tests/**/*.js'],
        languageOptions: { globals: globals.browser },
    },
    {
        files: ['*.config.js', 'tests/**/*.js'],
        languageOptions: { globals: globals.node },
    },
    {
        files: ['src/views/**/*.vue', 'src/components/**/*.vue', 'src/layouts/**/*.vue'],
        rules: {
            'no-restricted-imports': [
                'error',
                {
                    patterns: [
                        {
                            group: ['**/api/client.js', '**/api/http.js', '**/api/config.js', '**/endpoints.js'],
                            message:
                                'Call the domain API module; keep transport and endpoint details out of UI components.',
                        },
                    ],
                },
            ],
        },
    },
    {
        files: ['src/**/*.vue'],
        plugins: { content: { rules: { 'no-inline-ui-copy': noInlineUiCopy } } },
        rules: {
            'content/no-inline-ui-copy': 'error',
            'vue/no-bare-strings-in-template': [
                'error',
                {
                    allowlist: [
                        '—',
                        '–',
                        '·',
                        '/',
                        '(',
                        ')',
                        '→',
                        '⇒',
                        '×',
                        '*',
                        ':',
                        ',',
                        '.',
                        '%',
                        '#',
                        '‹',
                        '›',
                    ],
                    attributes: {
                        '/.*/': [
                            'title',
                            'label',
                            'placeholder',
                            'aria-label',
                            'alt',
                            'description',
                            'data-label',
                        ],
                    },
                },
            ],
        },
    },
    prettier,
]
