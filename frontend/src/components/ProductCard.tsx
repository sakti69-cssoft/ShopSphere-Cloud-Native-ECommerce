"use client";
import Link from "next/link";
import { usePathname,useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { Product } from "@/types";
import { money } from "@/data/mock";
import { useStore } from "@/context/store";
import { useAuth } from "@/context/auth";
import { SafeImage } from "./SafeImage";

export function ProductCard({product}:{product:Product}) {
  const {addToCart,toggleWishlist,inWishlist,wishlistLoading}=useStore();
  const {user}=useAuth();
  const router=useRouter(),pathname=usePathname();
  const [added,setAdded]=useState(false);
  const [message,setMessage]=useState("");
  const saved=inWishlist(product.id);
  const discount=Math.round((1-product.price/product.originalPrice)*100);
  useEffect(()=>{if(!added)return;const timer=setTimeout(()=>setAdded(false),1400);return()=>clearTimeout(timer)},[added]);
  return <article className="product-card">
    <div className="product-visual">
      {product.badge&&<span className={`badge ${product.badge.toLowerCase()}`}>{product.badge}</span>}
      <button className={`heart ${saved?"active":""}`} disabled={wishlistLoading} onClick={async()=>{if(!user){router.push(`/login?returnTo=${encodeURIComponent(pathname)}`);return}try{await toggleWishlist(product);setMessage(saved?"Removed":"Saved")}catch{setMessage("Try again")}}} aria-label={`${saved?"Remove":"Add"} ${product.title} ${saved?"from":"to"} wishlist`} aria-pressed={saved}>{saved?"♥":"♡"}</button>
      <Link style={{position:"absolute",inset:0}} href={`/product/${product.id}`} aria-label={`View ${product.title}`}><SafeImage src={product.image} alt={product.title} fill sizes="(max-width:600px) 50vw, (max-width:1100px) 33vw, 280px" fallbackLabel={product.title} category={product.category}/></Link>
    </div>
    <div className="product-info"><span className="brand">{product.brand}</span><Link href={`/product/${product.id}`}><h3>{product.title}</h3></Link><div className="rating"><b>★ {product.rating.toFixed(1)}</b><span>{product.reviewCount.toLocaleString("en-IN")} reviews</span></div><div className="price"><strong>{money(product.price)}</strong><del>{money(product.originalPrice)}</del><span>{discount}% off</span></div><p className="delivery">{product.delivery}</p>{message&&<span className="card-feedback" role="status">{message}</span>}<button className={`add-btn ${added?"added":""}`} onClick={async()=>{try{await addToCart(product);setAdded(true)}catch{}}} aria-live="polite">{added?"✓ Added to cart":"Add to cart"}</button></div>
  </article>
}
