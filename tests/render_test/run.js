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
				const browser = await puppeteer.launch({defaultViewport: {width: 1920, height:1080}});
				const page = await browser.newPage();

				await page.goto('http://localhost:8080/index.html');
				await sleep(5000);
				await page.screenshot({path: "tests/render_test/new.png"});

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
