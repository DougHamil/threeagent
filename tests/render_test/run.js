const puppeteer = require('puppeteer');
const express = require('express');
const looksSame = require('looks-same');

const app = express();
const port = 8080
var server = null;
var testPassed = true;

function sleep(ms){
    return new Promise(resolve=>{
        setTimeout(resolve,ms)
    })
}
app.use(express.static('tests/render_test'))
server = app.listen(port, () => {
  (async () => {
      try {
	console.log("Launching browser...");
        const browser = await puppeteer.launch({
         headless: true, // Use modern headless mode
         defaultViewport: { width: 1920, height: 1080 },
          args: [
          '--no-sandbox',                // Required for Docker
          '--disable-setuid-sandbox',
          '--disable-dev-shm-usage',     // Forces use of /tmp instead of /dev/shm to prevent hangs
          '--use-gl=angle',              // Essential for WebGL in headless
          '--use-angle=swiftshader',     // CPU-based WebGL rendering (fixes the context error)
          '--mute-audio'
          ]
	});
	console.log("Opening tab...");
        const page = await browser.newPage();

	console.log("Navigating to page...");
        await page.goto('http://localhost:8080/index.html');
	console.log("Sleeping for 5s...");
        await sleep(5000);
	console.log("Taking screenshot...");
        await page.screenshot({path: "tests/render_test/new.png"});

	console.log("Comparing to baseline...");
        looksSame.createDiff({
          reference: "tests/render_test/baseline.png",
          current: "tests/render_test/new.png",
          diff: "tests/render_test/diff.png",
          strict:true}, (err) => {
            console.log("Diff image generated.");
            console.log("Running regression test...");
            looksSame("tests/render_test/baseline.png", "tests/render_test/new.png", {strict: true}, (err, {equal}) => {
                if(!equal) {
                    console.error("Test failed. See tests/render_test/diff.png");
                    testPassed = false;
                    process.exit(1);
                } else {
                  console.log("Test passed.");
                }
            });
          });
        await browser.close();
      }
      catch (ex) {
          console.error(ex);
      }
      server.close();
  })();
});
