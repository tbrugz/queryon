import { createRequire } from 'node:module';

// Resolve plugins from src/main/frontend/node_modules so this config can live at qon-web/
// and match both src/main/webapp/ and src/main/frontend/.
const require = createRequire(new URL('./src/main/frontend/package.json', import.meta.url));
const js = require('@eslint/js');
const globals = require('globals');
const html = require('eslint-plugin-html');

// Vendor libraries loaded as classic <script src="..."> globals
const qonVendorGlobals = {
	d3: 'readonly',
	nv: 'readonly',
	ace: 'readonly',
	sigma: 'readonly',
	Prism: 'readonly',
	marked: 'readonly',
	difflib: 'readonly',
	diffview: 'readonly',
	typeahead: 'readonly',
	React: 'readonly',
	ReactDOM: 'readonly',
	GraphiQL: 'readonly',
};

// Shared symbols declared across src/main/webapp/js/*.js
const qonAppGlobals = Object.fromEntries(
	[
		'DIM_NULL_VALUE', 'addFilterWithValues', 'append2url', 'arrayContainsRetLabels',
		'arrayIntersect', 'authGetLoginUrl', 'authHasPermission', 'authInfo',
		'binaryDownloadExts', 'blobSqlTypes', 'btnActionStart', 'btnActionStop', 'byId',
		'checkForSqlWarnings', 'checkIfRelationExists', 'closeDialogs', 'closeMessages',
		'createMenuContent', 'd3multiseries', 'defaultNullObjectOption', 'deleteCookie', 'doDelete',
		'doHttpRequest', 'doRun', 'doTableOnLoad', 'escapeXML', 'formatDate',
		'getAttributeListFromObjectList', 'getColumnNames', 'getColumnNamesFromColgroup',
		'getColumnTypesFromColgroup', 'getColumnsFromColgroup', 'getColumnsFromRelation',
		'getColumnsRemarks', 'getColumnsTypesFromHash', 'getCookie', 'getCurrentModelElement',
		'getCurrentModelId', 'getCurrentRelation', 'getDescription', 'getId',
		'getKeyValsForRow', 'getLocationHash', 'getModelSetting', 'getParameterArrayStartWith',
		'getParameterByName', 'getParameterNamesStartWith', 'getParameters',
		'getParametersByName', 'getPivotURL', 'getPkCols', 'getQonData', 'getQueryUpdateUrl',
		'getQueryUrl', 'getScalarArrayFromValue', 'getSelectMultiOptions', 'getTableDimValues',
		'getValuesFromColumn', 'invertColors', 'isInteger', 'isMultiModel', 'isObject',
		'isQonQueriesPluginActive', 'isServiceActive', 'loadAuthInfo', 'loadModelStatus',
		'loadModels', 'loadQueries', 'loadQueries0', 'loadRelation', 'loadSelect',
		'loadSettings', 'makeHrefs', 'makeOneHref', 'menucontent', 'modelsInfo',
		'numericSqlTypes', 'nvD3LineChart', 'obj2encodedUrl', 'onQueryChanged',
		'onQueryChangedClean', 'operatorsInfo', 'pivotQueryActive', 'post', 'qonAjax',
		'qonAppContext', 'qonContextPath', 'queryOnUrl', 'refreshAuthInfo',
		'relationHasGrantOfType', 'relationHasGrantOfTypeAnyColumn', 'relationsHash',
		'removeAllFilters', 'removeHtmlxSpecialCols', 'removeMarker', 'removeParameter',
		'resetColors', 'runD3', 'runNvD3', 'safeTags', 'saveBlob', 'setCookie', 'setDisplayNone',
		'setParameters', 'setParametersValues', 'setSelectMultiOptions', 'settings',
		'showDialogMessage', 'showErrorMessages', 'showInfoMessages', 'showWarnMessages',
		'statusGetRawWarnings', 'statusGetWarnings', 'statusWarnings2Text', 'svgDownloadLink',
		'updateEnvVars', 'updateSelectValue', 'updateSelectedQueryStateParameters',
		'urlAddFilterOrderOffset', 'urlAddNamedParameters', 'urlGetQueryParameters',
		'writeRelations',
	].map(name => [name, 'writable']),
);

// Callbacks defined in individual HTML pages and invoked from shared webapp/js/*.js
const qonPageCallbacks = Object.fromEntries(
	[
		'doLoadQueries',
		'loadAuthInfoCallback',
		'loadMenuContentCallback',
		'loadModelStatusContentCallback',
		'loadModelsContentCallback',
		'updateFromFilters',
		'updateSelectedQueryState',
		'updateState',
		'updateUI',
	].map(name => [name, 'writable']),
);

export default [
	{
		ignores: [
			'target/**',
			'work/**',
			'src/main/frontend/node/**',
			'src/main/frontend/node_modules/**',
			'src/main/webapp/js/vendor/**',
			'src/main/webapp/swagger-ui/**',
			'src/main/webapp/map/js/jscolor/**',
			'**/* copy.*',
		],
	},

	// Node build script (ESM)
	{
		files: ['src/main/frontend/build.mjs'],
		...js.configs.recommended,
		languageOptions: {
			ecmaVersion: 2022,
			sourceType: 'module',
			globals: {
				...globals.node,
			},
		},
	},

	// Frontend bundler entry points (ESM)
	{
		files: ['src/main/frontend/src/**/*.js'],
		...js.configs.recommended,
		languageOptions: {
			ecmaVersion: 2022,
			sourceType: 'module',
			globals: {
				...globals.browser,
			},
		},
	},

	// Ace custom SQL mode (classic script using global `ace`)
	{
		files: ['src/main/frontend/ace/**/*.js'],
		...js.configs.recommended,
		languageOptions: {
			ecmaVersion: 2022,
			sourceType: 'script',
			globals: {
				...globals.browser,
				ace: 'readonly',
			},
		},
		rules: {
			...js.configs.recommended.rules,
			'no-unused-vars': ['warn', { args: 'none' }],
		},
	},

	// Swagger UI initializer (classic script)
	{
		files: ['src/main/frontend/swagger/**/*.js'],
		...js.configs.recommended,
		languageOptions: {
			ecmaVersion: 2022,
			sourceType: 'script',
			globals: {
				...globals.browser,
				SwaggerUIBundle: 'readonly',
				SwaggerUIStandalonePreset: 'readonly',
			},
		},
	},

	// Webapp classic JS files and inline <script> blocks in HTML
	{
		files: ['src/main/webapp/js/**/*.js', 'src/main/webapp/**/*.html'],
		plugins: {
			html,
		},
		...js.configs.recommended,
		languageOptions: {
			ecmaVersion: 2022,
			sourceType: 'script',
			globals: {
				...globals.browser,
				...qonVendorGlobals,
				...qonAppGlobals,
				...qonPageCallbacks,
			},
		},
		rules: {
			...js.configs.recommended.rules,
			// Allow files to declare the shared globals they export, while still catching same-scope redeclarations
			'no-redeclare': ['error', { builtinGlobals: false }],
			// Top-level functions/vars are shared across <script> tags and HTML onclick/onload handlers
			'no-unused-vars': ['warn', { vars: 'local', args: 'none' }],
		},
	},

	// Map page globals (Google Maps, jscolor, map/js/mapproc*.js)
	{
		files: ['src/main/webapp/map/**/*.html'],
		languageOptions: {
			globals: {
				google: 'readonly',
				jscolor: 'readonly',
				DEFAULT_FILL_COLOR: 'readonly',
				applySeriesDataAndStyle: 'readonly',
				changeColor: 'readonly',
				closeCatInfo: 'readonly',
				distinctValuesArray: 'readonly',
				genCategoriesFromLimits: 'readonly',
				getLinearCategoriesLimits: 'readonly',
				getLogCategoriesLimits: 'readonly',
				getQuantileCategoriesLimits: 'readonly',
				global_selectCatIdElements: 'writable',
				hexColorIsDark: 'readonly',
				isNumericArray: 'readonly',
				max: 'readonly',
				min: 'readonly',
				procStylesFromCategoriesMultipleColors: 'readonly',
				removeSeriesDataWhenPlacemarkNotFound: 'readonly',
				rgbColor2kmlColor: 'readonly',
				seriesValues: 'readonly',
			},
		},
	},
];
