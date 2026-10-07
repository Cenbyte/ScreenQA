// Headless Chromium/CDP check for the shared WebView JS, without third-party npm dependencies.
// Start an isolated headless browser with --remote-debugging-port=19227 first.
// Run from the repository root: node tools/verify_knowledge_password.mjs
import fs from 'node:fs';
import assert from 'node:assert/strict';

const endpoint=process.env.KNOWLEDGE_TEST_CDP || 'http://127.0.0.1:19227';
const targets=await (await fetch(endpoint+'/json/list')).json();
const target=targets.find(t=>t.type==='page');
assert.ok(target,'No test page found');
const socket=new WebSocket(target.webSocketDebuggerUrl);
await new Promise((resolve,reject)=>{socket.addEventListener('open',resolve,{once:true});socket.addEventListener('error',reject,{once:true});});
let id=0;const pending=new Map();const requests=[];
socket.addEventListener('message',event=>{
    const result=JSON.parse(event.data);
    if(result.id) {
        const task=pending.get(result.id);if(!task)return;pending.delete(result.id);
        result.error?task.reject(new Error(JSON.stringify(result.error))):task.resolve(result.result);
    }
    if(result.method==='Network.responseReceived')requests.push(result.params);
});
function send(method,params={}) {
    return new Promise((resolve,reject)=>{
        const key=++id;pending.set(key,{resolve,reject});socket.send(JSON.stringify({id:key,method,params}));
    });
}
async function evaluate(expression) {
    const result=await send('Runtime.evaluate',{expression,returnByValue:true,awaitPromise:true});
    if(result.exceptionDetails)throw new Error(JSON.stringify(result.exceptionDetails));
    return result.result.value;
}
const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));
async function until(expression,timeout=10000) {
    const start=Date.now();while(Date.now()-start<timeout) {
        const result=await evaluate(expression);if(result)return result;await pause(100);
    }
    throw new Error('Timed out waiting for '+expression);
}
const config=fs.readFileSync('app/src/main/java/cn/screenqa/lite/KnowledgeSourceConfig.java','utf8');
const sourceUrl=config.match(/String URL\s*=\s*"([^"]+)"/)[1];
const extractionCode=config.match(/String EXTRACTION_CODE\s*=\s*"([^"]+)"/)[1];
const script=fs.readFileSync('app/src/main/assets/knowledge_password.js','utf8').replace('__EXTRACTION_CODE__',JSON.stringify(extractionCode));
const passed=[];
try {
    await send('Page.enable');await send('Runtime.enable');await send('Network.enable');
    async function fixture(html) {
        const url='data:text/html;charset=utf-8,'+encodeURIComponent(html);
        await send('Page.navigate',{url});
        await until("document.readyState!=='loading' && location.href==="+JSON.stringify(url));
    }
    await fixture(`<html><head><script>window.calls=0</script></head><body><input id="pwd"><input id="sub" type="submit" onclick="calls++;window.code=document.getElementById('pwd').value"><script>${script}</script></body></html>`);
    await until('window.calls===1');assert.equal(await evaluate('window.code'),extractionCode);
    await evaluate(script);assert.equal(await evaluate('window.calls'),1);passed.push('Loading DOM waits for page handlers; submit exactly once across repeated probes');

    await fixture(`<html><head><script>window.calls=0</script></head><body><script>${script};setTimeout(function(){document.body.insertAdjacentHTML('beforeend','<input id="pwd"><input id="sub" type="submit" onclick="calls++">')},100)</script></body></html>`);
    await until('window.calls===1');assert.equal(await evaluate("document.getElementById('pwd').value"),extractionCode);passed.push('MutationObserver handles password form arriving after page load');

    await fixture(`<html><head><script>window.calls=0</script></head><body><input id="pwd" value="manual-code"><input id="sub" type="submit" onclick="calls++"><script>${script}</script></body></html>`);
    await pause(250);assert.equal(await evaluate('window.calls'),0);assert.equal(await evaluate("document.getElementById('pwd').value"),'manual-code');
    await evaluate("document.getElementById('sub').click()");assert.equal(await evaluate('window.calls'),1);passed.push('Existing manual input stays untouched; manual submit remains usable');

    await fixture(`<html><head><script>window.calls=0</script></head><body><input id="pwd"><script>${script};setTimeout(function(){document.body.insertAdjacentHTML('beforeend','<input id="sub" type="submit" onclick="calls++">')},100)</script></body></html>`);
    await until('window.calls===1');passed.push('Does not submit before a usable control exists');

    await fixture(`<html><head><script>window.calls=0</script></head><body><input id="pwd"><input id="sub" type="submit" onclick="calls++"><script>${script}</script></body></html>`);
    await until('window.calls===1');await evaluate("document.getElementById('pwd').value='user-retry';document.getElementById('sub').click()");
    await evaluate(script);assert.equal(await evaluate('window.calls'),2);assert.equal(await evaluate("document.getElementById('pwd').value"),'user-retry');passed.push('Failed auto-submit allows manual correction without automatic re-submission');

    console.log(JSON.stringify({fixtureChecks:passed},null,2));
    fs.mkdirSync('build-reasoning',{recursive:true});
    fs.writeFileSync('build-reasoning/knowledge-password-fixtures.json',JSON.stringify({passed},null,2));

    await send('Emulation.setDeviceMetricsOverride',{width:412,height:915,deviceScaleFactor:1,mobile:true});
    await send('Network.setUserAgentOverride',{userAgent:'Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/130.0.0.0 Mobile Safari/537.36'});
    const navigation=await send('Page.navigate',{url:sourceUrl});
    assert.ok(!navigation.errorText,navigation.errorText);
    await until("document.readyState!=='loading' && document.getElementById('pwd') && typeof window.file==='function'",20000);
    // Count real clicks before injecting exactly the production script.
    await evaluate("window.autoClicks=0;document.getElementById('sub').addEventListener('click',function(){window.autoClicks++})");
    await evaluate(script);
    await until("document.getElementById('pwdload').style.display==='none'",20000);
    const real=await evaluate("({title:document.title,code:document.getElementById('pwd').value,clicks:window.autoClicks,list:document.getElementById('infos').innerText,pwdHidden:document.getElementById('pwdload').style.display==='none',url:location.href})");
    assert.equal(real.code,extractionCode);assert.equal(real.clicks,1);assert.ok(real.pwdHidden);
    const ajax=requests.findLast(r=>r.response.url.includes('filemoreajax.php'));
    if(ajax) {
        const body=await send('Network.getResponseBody',{requestId:ajax.requestId});
        real.serverResponse=JSON.parse(body.base64Encoded?Buffer.from(body.body,'base64').toString():body.body);
    }
    fs.writeFileSync('build-reasoning/knowledge-password-live.json',JSON.stringify(real,null,2));
    const screenshot=await send('Page.captureScreenshot',{format:'png'});
    fs.writeFileSync('build-reasoning/knowledge-empty-folder.png',Buffer.from(screenshot.data,'base64'));
    console.log(JSON.stringify({liveChromium:real},null,2));
} finally { socket.close(); }
