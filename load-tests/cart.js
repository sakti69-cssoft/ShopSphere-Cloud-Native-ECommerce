import http from 'k6/http';
import {check,sleep,fail} from 'k6';
export const options={vus:1,iterations:3,thresholds:{checks:['rate==1'],http_req_duration:['p(95)<2000']}};
export default function(){
 if(!__ENV.TOKEN||!__ENV.USER_ID||!__ENV.PRODUCT_ID)fail('Provide dedicated QA TOKEN, USER_ID, PRODUCT_ID');
 const url=(__ENV.BASE_URL||'http://localhost/api/v1')+'/cart/'+__ENV.USER_ID;
 const params={headers:{Authorization:'Bearer '+__ENV.TOKEN,'Content-Type':'application/json'}};
 check(http.post(url+'/items',JSON.stringify({productId:__ENV.PRODUCT_ID,quantity:1}),params),{'add':r=>r.status===200});
 check(http.get(url,params),{'read':r=>r.status===200});
 check(http.del(url+'/items/'+__ENV.PRODUCT_ID,null,params),{'remove':r=>r.status===200});
 sleep(1);
}
