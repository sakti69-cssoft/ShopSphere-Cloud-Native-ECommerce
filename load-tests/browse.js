import http from 'k6/http';
import {check,sleep} from 'k6';
const base=__ENV.BASE_URL||'http://localhost/api/v1';
export const options={vus:Number(__ENV.VUS||2),duration:__ENV.DURATION||'20s',thresholds:{http_req_failed:['rate<0.01'],http_req_duration:['p(95)<2000']}};
export default function(){
 const response=http.get(base+'/products?size=12');
 check(response,{'browse succeeds':r=>r.status===200});
 if(response.status===200){const products=response.json('content');if(products.length)check(http.get(base+'/products/'+products[0].id),{'detail succeeds':r=>r.status===200});}
 check(http.get(base+'/products/search?q=phone'),{'search succeeds':r=>r.status===200});
 sleep(1);
}
