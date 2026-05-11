// Karma configuration for local and CI headless test runs.
module.exports = function (config) {
  config.set({
    basePath: '',
    frameworks: ['jasmine', '@angular-devkit/build-angular'],
    plugins: [
      require('karma-jasmine'),
      require('karma-chrome-launcher'),
      require('karma-jasmine-html-reporter'),
      require('karma-coverage'),
      require('@angular-devkit/build-angular/plugins/karma')
    ],
    client: {
      jasmine: {},
      clearContext: false
    },
    reporters: ['progress', 'kjhtml'],
    browsers: ['ChromeHeadlessNoGpu'],
    customLaunchers: {
      ChromeHeadlessNoGpu: {
        base: 'Chrome',
        flags: [
          '--headless=new',
          '--disable-gpu',
          '--disable-gpu-compositing',
          '--disable-gpu-sandbox',
          '--disable-software-rasterizer',
          '--disable-dev-shm-usage',
          '--no-sandbox',
          '--no-first-run',
          '--no-default-browser-check',
          '--remote-debugging-port=0'
        ]
      }
    },
    captureTimeout: 180000,
    browserNoActivityTimeout: 180000,
    restartOnFileChange: true
  });
};
