import http from 'k6/http';
import {check} from 'k6';
import {Counter} from 'k6/metrics';
const limited=new Counter('rate_limited');
export const options={vus:10,iterations:30,thresholds:{rate_limited:['count>0'],checks:['rate==1']}};
export default function(){
 const r=http.post((__ENV.BASE_URL||'http://localhost/api/v1')+'/auth/login',JSON.stringify({email:'invalid-load-test@example.invalid',password:'not-a-real-password'}),{headers:{'Content-Type':'application/json'}});
 if(r.status===429)limited.add(1);
 check(r,{'controlled authentication rejection':r=>[401,429].includes(r.status)});
}
