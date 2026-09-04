"use client";

import Link from "next/link";
import {FormEvent,useCallback,useEffect,useMemo,useState} from "react";
import {useRouter} from "next/navigation";
import {CartSummary} from "@/components/CartSummary";
import {useAuth} from "@/context/auth";
import {useStore} from "@/context/store";
import {addressApi} from "@/lib/api/addresses";
import {orderApi} from "@/lib/api/orders";
import {checkoutKey,completeCheckout} from "@/lib/checkout";
import type{ApiOrderQuote,ApiSavedAddress}from"@/lib/api/types";

export function CheckoutExperience(){
 const{user}=useAuth(),{cart,clearCart}=useStore(),router=useRouter();
 const[addresses,setAddresses]=useState<ApiSavedAddress[]>([]),[selectedAddress,setSelectedAddress]=useState("");
 const[couponDraft,setCouponDraft]=useState(""),[appliedCoupon,setAppliedCoupon]=useState("");
 const[quote,setQuote]=useState<ApiOrderQuote|null>(null),[loading,setLoading]=useState(true),[quoting,setQuoting]=useState(false),[busy,setBusy]=useState(false);
 const[error,setError]=useState(""),[couponError,setCouponError]=useState("");
 const items=useMemo(()=>cart.map(item=>({productId:item.product.id,quantity:item.quantity})),[cart]);
 const refreshQuote=useCallback(async(code:string)=>{if(!user||!items.length)return null;setQuoting(true);try{const next=await orderApi.quote({userId:user.id,items,couponCode:code||null});setQuote(next);return next}finally{setQuoting(false)}},[items,user]);

 useEffect(()=>{if(!user)return;let active=true;void Promise.resolve().then(()=>{setLoading(true);return addressApi.list()}).then(rows=>{if(!active)return;setAddresses(rows);setSelectedAddress(current=>current||rows.find(address=>address.defaultAddress)?.id||rows[0]?.id||"")}).catch(e=>{if(active)setError(e instanceof Error?e.message:"Could not load saved addresses.")}).finally(()=>{if(active)setLoading(false)});return()=>{active=false}},[user]);
 useEffect(()=>{if(user&&items.length)void Promise.resolve().then(()=>refreshQuote(appliedCoupon)).catch(e=>setError(e instanceof Error?e.message:"Could not calculate secure totals."))},[appliedCoupon,items,refreshQuote,user]);

 async function applyCoupon(event:FormEvent<HTMLFormElement>){
  event.preventDefault();setCouponError("");setError("");
  const code=couponDraft.trim().toUpperCase();
  if(!code){setAppliedCoupon("");await refreshQuote("");return}
  try{const next=await refreshQuote(code);setAppliedCoupon(next?.couponCode??code);setCouponDraft(next?.couponCode??code)}
  catch(e){setCouponError(e instanceof Error?e.message:"Coupon could not be applied.")}
 }

 async function place(){
  if(!user||!items.length||!selectedAddress)return;
  const fingerprint=JSON.stringify({userId:user.id,items,addressId:selectedAddress,couponCode:appliedCoupon||null});
  setBusy(true);setError("");
  try{
   const order=await orderApi.create({userId:user.id,items,addressId:selectedAddress,couponCode:appliedCoupon||null},checkoutKey(fingerprint));
   sessionStorage.setItem("shopsphere-last-order",JSON.stringify(order));completeCheckout();await clearCart();router.push(`/order-success?id=${order.id}`);
  }catch(e){setError(e instanceof Error?e.message:"Could not place the order. Your cart is unchanged; retry safely.")}
  finally{setBusy(false)}
 }

 const selected=addresses.find(address=>address.id===selectedAddress);
 if(!cart.length)return <div className="page-wrap"><div className="empty"><h2>Your cart is empty</h2><Link href="/search">Continue shopping</Link></div></div>;
 return <div className="page-wrap checkout-page">
  <div className="checkout-heading"><div><small className="eyebrow">SECURE CHECKOUT</small><h1 className="page-title">Review and place your order</h1></div><div className="checkout-steps"><span className="done">Cart</span><span className="active">Address</span><span>Review</span></div></div>
  <div className="checkout-layout"><section className="checkout-main">
   <div className="panel checkout-section"><div className="checkout-section-title"><span>1</span><div><small>DELIVERY</small><h2>Select a saved address</h2></div><Link href="/account/addresses">Manage addresses</Link></div>
    {loading?<div className="state-card">Loading saved addresses…</div>:!addresses.length?<div className="empty compact"><h3>No saved address</h3><p>Add a delivery address before placing your order.</p><Link className="btn-primary" href="/account/addresses">Add address</Link></div>:<div className="checkout-addresses">{addresses.map(address=><label className={`checkout-address ${selectedAddress===address.id?"selected":""}`} key={address.id}><input type="radio" name="savedAddress" value={address.id} checked={selectedAddress===address.id} onChange={()=>setSelectedAddress(address.id)}/><span><b>{address.recipientName}</b>{address.defaultAddress&&<em>Default</em>}<small>{address.line1}{address.line2?`, ${address.line2}`:""}<br/>{address.city}, {address.state} {address.postalCode}<br/>{address.phone}</small></span></label>)}</div>}
   </div>
   <div className="panel checkout-section"><div className="checkout-section-title"><span>2</span><div><small>PROMOTION</small><h2>Apply a coupon</h2></div></div>
    <form className="coupon-form" onSubmit={applyCoupon}><input aria-label="Coupon code" placeholder="Enter coupon code" maxLength={32} pattern="[A-Za-z0-9_-]{3,32}" value={couponDraft} onChange={e=>{const value=e.target.value.toUpperCase();setCouponDraft(value);if(appliedCoupon&&value!==appliedCoupon)setAppliedCoupon("")}}/><button className="btn-secondary" type="submit" disabled={quoting}>{quoting?"Checking…":"Apply"}</button></form>
    {couponError&&<p className="form-error" role="alert">{couponError}</p>}{appliedCoupon&&quote&&<p className="coupon-success" role="status">✓ {quote.discountDescription} applied securely</p>}<small className="coupon-hint">Demo promotions: WELCOME10 or SAVE100. Eligibility is verified by the server.</small>
   </div>
   <div className="panel checkout-section"><div className="checkout-section-title"><span>3</span><div><small>REVIEW</small><h2>Order and payment</h2></div></div>
    <div className="checkout-items">{cart.map(item=><div key={item.product.id}><span>{item.product.title} <small>× {item.quantity}</small></span><b>₹{(item.product.price*item.quantity).toLocaleString("en-IN")}</b></div>)}</div>
    {selected&&<div className="selected-delivery"><small>DELIVER TO</small><b>{selected.recipientName}</b><span>{selected.line1}, {selected.city}, {selected.state} {selected.postalCode}</span></div>}
    <label className="payment-option"><input type="radio" name="payment" defaultChecked/><b>Cash on delivery / simulated payment</b></label>
   </div>
   {error&&<p className="form-error" role="alert">{error}</p>}<button className="btn-primary place-order" type="button" onClick={()=>void place()} disabled={busy||loading||quoting||!selectedAddress||!quote}>{busy?"Placing your order safely…":"Place order securely"}</button>
  </section><CartSummary checkout quote={quote}/></div>
 </div>
}
