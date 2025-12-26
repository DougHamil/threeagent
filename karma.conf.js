process.env.CHROME_BIN = require('puppeteer').executablePath()

module.exports = function(config) {
    config.set({
        // 1. Switch to your custom CI launcher
        browsers: ['ChromeHeadlessCI'],

        // 2. Define the custom launcher with required flags for Docker
        customLaunchers: {
            ChromeHeadlessCI: {
                base: 'ChromeHeadless',
                flags: [
                    '--no-sandbox',                // Required for Docker
                    '--disable-gpu',               // Prevents the GPU crashes in your logs
                    '--disable-dev-shm-usage',      // Prevents memory issues in CI
                    '--disable-software-rasterizer',
                    '--remote-debugging-port=9222'
                ]
            }
        },

        // 3. Increase timeouts for slower CI environments
        browserNoActivityTimeout: 60000,
        browserDisconnectTimeout: 20000,
        captureTimeout: 60000,

        // The directory where the output file lives
        basePath: 'target',
        // The file itself
        files: ['ci.js'],
        frameworks: ['cljs-test'],
        plugins: ['karma-cljs-test', 'karma-chrome-launcher'],
        colors: true,
        logLevel: config.LOG_INFO,
        client: {
            args: ["shadow.test.karma.init"],
            singleRun: true
        },
        junitReporter: {
            outputDir: 'reports/karma'
        }
    });
}
