// Local, synthetic QA only. Creates QA accounts/products/orders; never deletes volumes.
import {randomUUID} from "node:crypto";
import {execFileSync} from "node:child_process";
import {mkdirSync,writeFileSync} from "node:fs";
const base=process.env.BASE_URL||"http://localhost/api/v1";
if(!/^http:\/\/(localhost|127\.0\.0\.1)(:\d+)?\/api\/v1$/.test(base))throw Error("This mutation test is restricted to localhost");
const results=[];
const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));
async function call(path,method="GET",body,token,expected=200,extra={}){
 const response=await fetch(base+path,{method,headers:{"Content-Type":"application/json",...(token?{Authorization:"Bearer "+token}:{}),...extra},...(body?{body:JSON.stringify(body)}:{})});
 const text=await response.text();if(response.status!==expected)throw Error(method+" "+path+" expected "+expected+" got "+response.status+" "+text.slice(0,300));
 return text?JSON.parse(text):null;
}
function pass(name){results.push(name);console.log("PASS",name);}
function check(value,name){if(!value)throw Error(name);pass(name);}
async function eventually(path,token){let last;for(let attempt=0;attempt<30;attempt++){try{return await call(path,"GET",null,token)}catch(error){last=error;await pause(1000)}}throw last;}
const stamp=randomUUID().slice(0,8),password="QaOnly!"+randomUUID();
async function account(role){
 const email="qa-"+role+"-"+stamp+"@example.invalid";
 const user=await call("/auth/register","POST",{firstName:"QA",lastName:role,email,password,phone:"9999999999"},null,201);
 if(role==="admin")execFileSync("docker",["compose","exec","-T","mysql","sh","-c",'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$AUTH_DB_NAME"'],{input:"UPDATE users SET role='ADMIN' WHERE email='"+email+"';",stdio:["pipe","pipe","pipe"]});
 await pause(700);
 const login=await call("/auth/login","POST",{email,password});
 return {...login,email,id:login.user.id??user.id};
}
const admin=await account("admin"),customer=await account("customer");
mkdirSync(".validation",{recursive:true});
writeFileSync(".validation/qa-session.json",JSON.stringify({email:admin.email,password}),{mode:0o600});
pass("Register/login dedicated customer and admin");
await call("/orders/user/"+customer.id,"GET",null,null,401);
await call("/orders/user/"+customer.id,"GET",null,"invalid.jwt.token",401);
await call("/auth/me","GET",null,customer.refreshToken,401);
await call("/products/admin","GET",null,customer.accessToken,403);
await call("/products","POST",{},customer.accessToken,403);
await call("/cart/"+admin.id,"GET",null,customer.accessToken,403);
pass("401 missing/invalid/refresh JWT; 403 admin and cart ownership");
const body={sku:"QA-"+stamp,name:"QA Smartphone "+stamp,slug:"qa-phone-"+stamp,description:"Synthetic local validation smartphone",brand:"ShopSphere QA",category:"Mobiles",price:999,originalPrice:1199,discountPercentage:16.68,rating:4.5,reviewCount:1,imageUrls:["/media/product-phone.jpg"],specifications:{Purpose:"Local QA"},active:true};
const product=await call("/products","POST",body,admin.accessToken,201);
await call("/products/"+product.id,"PUT",{...body,description:"Updated through real admin API"},admin.accessToken);
check((await call("/products/admin","GET",null,admin.accessToken)).content.some(p=>p.id===product.id),"Admin product create/update/list");
check((await call("/products/search?q="+encodeURIComponent(stamp))).content.some(p=>p.id===product.id),"Product browse/search");
check((await call("/products/"+product.id)).price===999,"Product details");
await call("/inventory/"+product.id+"/stock","PUT",{sku:body.sku,quantityAvailable:25,reorderLevel:5},admin.accessToken);
check((await call("/inventory/"+product.id,"GET",null,admin.accessToken)).quantityAvailable===25,"Admin inventory update/read");
const cart=await call("/cart/"+customer.id+"/items","POST",{productId:product.id,quantity:1},customer.accessToken,201);
check(cart.subtotal===999,"Cart authoritative price");
const intent={userId:customer.id,items:[{productId:product.id,quantity:1,unitPrice:1,productName:"Forged",sku:"FAKE"}],discount:999999,deliveryFee:-100,shippingAddress:{recipient:"QA Customer",line1:"Synthetic QA Address",line2:"",city:"Test City",state:"Test State",postalCode:"560001",country:"India",phone:"9999999999"}};
const key="qa-"+randomUUID();
const order=await call("/orders","POST",intent,customer.accessToken,201,{"Idempotency-Key":key});
check(order.items[0].unitPrice===999&&order.totalAmount===1098&&order.discount===0&&order.items[0].sku===body.sku,"Tampered price ignored; INR 999 + 99 server total");
const duplicate=await call("/orders","POST",intent,customer.accessToken,201,{"Idempotency-Key":key});
check(duplicate.id===order.id,"Duplicate checkout returns same order");
await call("/orders","POST",{...intent,shippingAddress:{...intent.shippingAddress,city:"Different"}},customer.accessToken,409,{"Idempotency-Key":key});
check((await call("/inventory/"+product.id,"GET",null,admin.accessToken)).quantityReserved===1,"No double reservation");
check((await call("/orders/user/"+customer.id,"GET",null,customer.accessToken)).some(o=>o.id===order.id),"Customer order history");
await call("/orders/"+order.id,"GET",null,customer.accessToken);
await call("/orders/"+order.id+"/status","PUT",{status:"PROCESSING"},customer.accessToken,403);
await call("/orders/"+order.id+"/status","PUT",{status:"PROCESSING"},admin.accessToken);
check((await call("/orders","GET",null,admin.accessToken)).some(o=>o.id===order.id),"Admin order list/status");
const apps=["auth-service","product-service","inventory-service","cart-service","order-service","gateway","nginx"];
execFileSync("docker",["compose","--profile","local","restart",...apps],{stdio:"pipe",timeout:120000});
execFileSync("docker",["compose","--profile","local","up","-d","--wait","--wait-timeout","300"],{stdio:"pipe",timeout:330000});
await eventually("/products?size=1");
check((await eventually("/auth/me",customer.accessToken)).id===customer.id,"Auth persists after application restart");
check((await eventually("/products/"+product.id)).price===999,"Mongo product persists after restart");
check((await eventually("/inventory/"+product.id,admin.accessToken)).quantityReserved===1,"Inventory persists after restart");
check((await eventually("/cart/"+customer.id,customer.accessToken)).subtotal===999,"Redis cart persists after restart");
check((await eventually("/orders/"+order.id,customer.accessToken)).status==="PROCESSING","Order persists after restart");
check((await call("/orders","POST",intent,customer.accessToken,201,{"Idempotency-Key":key})).id===order.id,"Durable idempotency survives restart");
await call("/orders/"+order.id+"/cancel","POST",null,customer.accessToken);
check((await call("/inventory/"+product.id,"GET",null,admin.accessToken)).quantityReserved===0,"Eligible cancellation releases inventory");
const disposable=await call("/products","POST",{...body,sku:"DELETE-"+stamp,slug:"delete-"+stamp},admin.accessToken,201);
await call("/products/"+disposable.id,"DELETE",null,admin.accessToken,204);
await call("/products/"+disposable.id,"GET",null,null,404);
pass("Admin product deletion (only synthetic disposable product)");
writeFileSync(".validation/e2e-results.json",JSON.stringify({timestamp:new Date().toISOString(),results,productId:product.id,orderId:order.id},null,2));
console.log("E2E complete:",results.length,"checks; only synthetic QA data created.");
