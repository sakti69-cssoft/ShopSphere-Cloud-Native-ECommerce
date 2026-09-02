const KEY="shopsphere-checkout-attempt";
export function checkoutKey(fingerprint:string){const stored=sessionStorage.getItem(KEY);if(stored){try{const value=JSON.parse(stored)as{fingerprint:string;key:string};if(value.fingerprint===fingerprint)return value.key}catch{}}const key=crypto.randomUUID();sessionStorage.setItem(KEY,JSON.stringify({fingerprint,key}));return key}
export function completeCheckout(){sessionStorage.removeItem(KEY)}
