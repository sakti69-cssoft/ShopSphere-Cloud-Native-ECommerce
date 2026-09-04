"use client";

import Link from "next/link";
import { useCallback,useEffect,useMemo,useState } from "react";
import { useParams,usePathname,useRouter } from "next/navigation";
import { ProductRow } from "@/components/ProductRow";
import { SafeImage } from "@/components/SafeImage";
import { useAuth } from "@/context/auth";
import { useStore } from "@/context/store";
import { money } from "@/data/mock";
import { inventoryApi } from "@/lib/api/inventory";
import { toProduct } from "@/lib/api/mappers";
import { productApi } from "@/lib/api/products";
import type { ApiReview } from "@/lib/api/types";
import type { Product } from "@/types";

export default function ProductPage() {
 const{id}=useParams<{id:string}>();
 const router=useRouter(),pathname=usePathname();
 const{user}=useAuth();
 const{addToCart,toggleWishlist,inWishlist,wishlistLoading}=useStore();
 const[p,setP]=useState<Product|null>(null),[related,setRelated]=useState<Product[]>([]),[reviews,setReviews]=useState<ApiReview[]>([]);
 const[stock,setStock]=useState<number|null>(null),[quantity,setQuantity]=useState(1),[selectedImage,setSelectedImage]=useState(0);
 const[error,setError]=useState(""),[message,setMessage]=useState(""),[submitting,setSubmitting]=useState(false);
 const[rating,setRating]=useState(5),[title,setTitle]=useState(""),[text,setText]=useState("");
 const mine=useMemo(()=>reviews.find(review=>review.userId===user?.id),[reviews,user]);

 const load=useCallback(async()=>{try{const api=await productApi.get(id);const product=toProduct(api);const[relatedProducts,reviewPage,inventory]=await Promise.all([productApi.related(id,4),productApi.reviews(id,0,10),inventoryApi.get(id).catch(()=>null)]);setError("");setP(product);setRelated(relatedProducts.map(toProduct));setReviews(reviewPage.content);setStock(inventory?inventory.quantityAvailable-inventory.quantityReserved:null);const own=reviewPage.content.find(review=>review.userId===user?.id);setRating(own?.rating??5);setTitle(own?.title??"");setText(own?.text??"")}catch(e){setError(e instanceof Error?e.message:"Product unavailable")}},[id,user?.id]);
 useEffect(()=>{void Promise.resolve().then(load)},[load]);

 if(error)return <div className="page-wrap"><div className="state-card" role="alert"><h1>Product unavailable</h1><p>{error}</p><Link href="/search">Return to catalog</Link></div></div>;
 if(!p)return <div className="page-wrap"><div className="product-skeleton" role="status" aria-label="Loading product"><div/><div><i/><i/><i/><i/></div></div></div>;
 const off=p.originalPrice>0?Math.round((1-p.price/p.originalPrice)*100):0;
 const outOfStock=stock===0;
 const maxQuantity=Math.max(1,Math.min(stock??10,10));
 const images=p.images.length?p.images:[p.image];
 const wishlistSaved=inWishlist(p.id);

 const saveWishlist=async()=>{if(!user){router.push(`/login?returnTo=${encodeURIComponent(pathname)}`);return}try{await toggleWishlist(p);setMessage(wishlistSaved?"Removed from your wishlist.":"Saved to your wishlist.")}catch(e){setMessage(e instanceof Error?e.message:"Could not update your wishlist.")}};
 const submitReview=async(event:React.FormEvent)=>{event.preventDefault();if(!user){router.push(`/login?returnTo=${encodeURIComponent(pathname)}`);return}setSubmitting(true);try{if(mine)await productApi.updateReview(id,mine.id,{rating,title,text});else await productApi.createReview(id,{rating,title,text});setMessage(mine?"Your review was updated.":"Thanks—your review is now live.");await load()}catch(e){setMessage(e instanceof Error?e.message:"Could not save your review.")}finally{setSubmitting(false)}};

 return <div className="page-wrap">
  <div className="breadcrumb"><Link href="/">Home</Link> / <Link href={`/search?category=${encodeURIComponent(p.category)}`}>{p.category}</Link> / {p.title}</div>
  {message&&<div className="detail-notice" role="status">{message}</div>}
  <section className="detail-grid">
   <div className="gallery"><div className="gallery-main"><SafeImage src={images[selectedImage]??p.image} alt={p.title} fill priority sizes="(max-width:760px) 100vw, 50vw" fallbackLabel={p.title} category={p.category}/></div>{images.length>1&&<div className="gallery-thumbs">{images.map((image,index)=><button key={`${image}-${index}`} className={selectedImage===index?"active":""} onClick={()=>setSelectedImage(index)} aria-label={`Show image ${index+1}`}><SafeImage src={image} alt="" fill sizes="74px" fallbackLabel={p.title} category={p.category}/></button>)}</div>}</div>
   <div className="detail-info"><span className="brand">{p.brand} · Official store</span><h1>{p.title}</h1><div className="rating"><b>★ {p.rating.toFixed(1)}</b><a href="#reviews">{p.reviewCount.toLocaleString("en-IN")} verified customer {p.reviewCount===1?"review":"reviews"}</a></div><div className="price detail-price"><strong>{money(p.price)}</strong> <del>{money(p.originalPrice)}</del> {off>0&&<span>{off}% off</span>}</div><p className="detail-copy">Inclusive of all taxes. {p.description}</p><div className={`stock-box ${outOfStock?"out":""}`}><b>{stock===null?"Checking availability…":outOfStock?"Currently out of stock":`${stock} available for delivery`}</b><small>{outOfStock?"Explore a related product below.":p.delivery}</small></div><div className="purchase-row"><label htmlFor="quantity">Quantity</label><div className="qty"><button disabled={quantity<=1} onClick={()=>setQuantity(value=>Math.max(1,value-1))} aria-label="Decrease quantity">−</button><b id="quantity">{quantity}</b><button disabled={outOfStock||quantity>=maxQuantity} onClick={()=>setQuantity(value=>Math.min(maxQuantity,value+1))} aria-label="Increase quantity">+</button></div>{stock!==null&&stock>0&&<small>Maximum {maxQuantity} per order</small>}</div><div className="detail-actions"><button className="cart" disabled={outOfStock} onClick={async()=>{try{await addToCart(p,quantity);setMessage(`${quantity} × ${p.title} added to cart.`)}catch(e){setMessage(e instanceof Error?e.message:"Could not add to cart.")}}}>{outOfStock?"Out of stock":"Add to cart"}</button><button disabled={wishlistLoading} onClick={()=>void saveWishlist()}>{wishlistSaved?"♥ Saved":"♡ Save"}</button></div><p><b>Sold by</b> Sphere Retail · Secure packaging</p></div>
  </section>
  {Object.keys(p.specs).length>0&&<section className="section detail-section"><div className="section-head"><h2>Product specifications</h2></div><div className="spec-grid">{Object.entries(p.specs).map(([key,value])=><div className="spec" key={key}><span>{key}</span><b>{value}</b></div>)}</div></section>}
  <section className="section detail-section review-section" id="reviews"><div className="section-head"><div><span>Customer voices</span><h2>Ratings & reviews</h2></div><strong className="rating-summary">★ {p.rating.toFixed(1)} <small>({p.reviewCount})</small></strong></div><div className="review-layout"><div>{reviews.length?<div className="review-list">{reviews.map(review=><article className="review" key={review.id}><div className="review-meta"><b>{"★".repeat(review.rating)}<span>{"★".repeat(5-review.rating)}</span></b><time dateTime={review.createdAt}>{new Date(review.createdAt).toLocaleDateString("en-IN",{day:"numeric",month:"short",year:"numeric"})}</time></div>{review.title&&<h3>{review.title}</h3>}<p>{review.text}</p><small>{review.displayName}</small>{review.userId===user?.id&&<button className="link-btn" onClick={async()=>{setSubmitting(true);try{await productApi.removeReview(id,review.id);setMessage("Your review was removed.");await load()}catch(e){setMessage(e instanceof Error?e.message:"Could not remove review.")}finally{setSubmitting(false)}}}>Remove my review</button>}</article>)}</div>:<div className="empty-review"><h3>No reviews yet</h3><p>Be the first customer to share a thoughtful review.</p></div>}</div><form className="review-form" onSubmit={submitReview}><h3>{mine?"Update your review":"Review this product"}</h3>{user?<><fieldset><legend>Your rating</legend><div className="star-picker">{[1,2,3,4,5].map(value=><button type="button" key={value} onClick={()=>setRating(value)} className={value<=rating?"active":""} aria-label={`${value} star rating`}>★</button>)}</div></fieldset><label>Review title<input value={title} maxLength={120} onChange={event=>setTitle(event.target.value)} placeholder="Sum up your experience"/></label><label>Your review<textarea value={text} required minLength={5} maxLength={2000} onChange={event=>setText(event.target.value)} placeholder="What did you like or dislike?"/></label><button className="btn-primary" disabled={submitting}>{submitting?"Saving…":mine?"Update review":"Submit review"}</button></>:<p><Link href={`/login?returnTo=${encodeURIComponent(pathname)}`}>Sign in</Link> to write a verified customer review.</p>}</form></div></section>
  {related.length>0&&<ProductRow title="You may also like" kicker="Related products" products={related}/>}
 </div>;
}
