import {execFileSync} from "node:child_process";
import {readFileSync} from "node:fs";
const files=execFileSync("git",["ls-files","--cached","--others","--exclude-standard","-z"],{encoding:"utf8"}).split("\0").filter(Boolean);
const forbidden=/(^|\/)(\.env(\..*)?|credentials|id_rsa|id_ed25519)$|\.(pem|key)$|\.tfstate(\.|$)|(^|\/)(node_modules|target|\.next|\.terraform)\//;
const secretPatterns=[/-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----/,/AKIA[0-9A-Z]{16}/,/gh[pousr]_[A-Za-z0-9]{30,}/];
let failed=false;
for(const path of new Set(files)){
 if(path.endsWith(".example"))continue;
 if(forbidden.test(path)){console.error("Forbidden tracked/candidate path:",path);failed=true;continue;}
 if(/\.(png|jpg|jpeg|webp|ico|woff2?)$/.test(path))continue;
 const content=readFileSync(path,"utf8");
 if(secretPatterns.some(p=>p.test(content))){console.error("Potential secret in:",path);failed=true;}
}
if(failed)process.exit(1);
console.log("Secret hygiene passed; ignored environment files were not read.");
