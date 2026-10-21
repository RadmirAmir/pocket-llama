const {chromium}=require('playwright');
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.CHAT_QA_BROWSER||undefined,args:['--no-sandbox','--disable-dev-shm-usage']});
 const page=await browser.newPage(),script=fs.readFileSync(path.resolve(__dirname,'../app/src/main/assets/search/browser-extract.js'),'utf8');
 await page.route('https://www.google.com/**',route=>route.fulfill({contentType:'text/html',body:`<html><body><div id="search">
 <div class="MjjYud"><a href="https://example.org/a"><h3>Local news headline</h3></a><p>Published evidence from the search page.</p><time datetime="2026-10-21T08:00:00Z">today</time></div>
 <div class="MjjYud"><a href="/url?q=https%3A%2F%2Fexample.org%2Fb"><h3>Second result</h3></a></div>
 <div class="MjjYud"><a href="javascript:alert(1)"><h3>Bad scheme</h3></a></div>
 <div class="MjjYud" data-text-ad="1"><a href="https://ads.example/ad"><h3>Advertisement</h3></a></div>
 <div class="MjjYud"><a href="https://example.org/a"><h3>Duplicate</h3></a></div></div></body></html>`}));
 await page.goto('https://www.google.com/search?q=synthetic+fixture');
 let result=JSON.parse(await page.evaluate(script));assert.equal(result.sources.length,2);assert.equal(result.sources[0].url,'https://example.org/a');assert(result.sources[0].text.includes('Published evidence'));assert.equal(result.sources[0].published,'2026-10-21T08:00:00.000Z');assert.equal(result.sources[1].url,'https://example.org/b');assert.equal(result.sources[1].headlineOnly,true);assert.equal(result.challenge,false);
 await page.setContent('<p>Unusual traffic. CAPTCHA. Verify you are human.</p><button id="solve">Continue</button>');
 result=JSON.parse(await page.evaluate(script));assert.equal(result.sources.length,0);assert.equal(result.challenge,true);assert.equal(await page.locator('#solve').count(),1,'Reader never clicks a challenge');
 await page.setContent('<div data-testid="result"><a data-testid="result-title-a" href="https://example.org/c">A result</a><p>Third source evidence</p></div>');
 result=JSON.parse(await page.evaluate(script));assert.equal(result.sources.length,1);assert(result.sources[0].text.includes('Third source evidence'));
 await browser.close();console.log('PASS: real browser DOM extraction, public links, dates, headline-only flags, no ads/duplicates and no challenge automation');
})().catch(e=>{console.error(e);process.exit(1);});
