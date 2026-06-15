// www/honeywellscanner.js
var exec = require('cordova/exec');

var HoneywellScanner = {
  setScannerEnabled: function (enable, success, error) {
    exec(success, error, 'HoneywellScanner', 'setScannerEnabled', [!!enable]);
  }
}

module.exports = HoneywellScanner;
