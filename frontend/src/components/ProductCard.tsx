"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { Product } from "@/types";
import { money } from "@/data/mock";
import { useStore } from "@/context/store";
import { SafeImage } from "./SafeImage";

export function ProductCard({product}:{product:Product}) {
  const {addToCart,toggleWishlist,inWishlist}=useStore();
  const [added,setAdded]=useState(false);
  const saved=inWishlist(product.id);
  const discount=Math.round((1-product.price/product.originalPrice)*100);
  useEffect(()=>{if(!added)return;const timer=setTimeout(()=>setAdded(false),1400);return()=>clearTimeout(timer)},[added]);
  return <article className="product-card">
    <div className="product-visual">
      {product.badge&&<span className={`badge ${product.badge.toLowerCase()}`}>{product.badge}</span>}
      <button className={`heart ${saved?"active":""}`} onClick={()=>toggleWishlist(product)} aria-label={`${saved?"Remove":"Add"} ${product.title} ${saved?"from":"to"} wishlist`} aria-pressed={saved}>♡</button>
      <Link style={{position:"absolute",inset:0}} href={`/product/${product.id}`} aria-label={`View ${product.title}`}><SafeImage src={product.image} alt={product.title} fill sizes="(max-width:600px) 50vw, (max-width:1100px) 33vw, 280px" fallbackLabel={product.title} category={product.category}/></Link>
    </div>
    <div className="product-info"><span className="brand">{product.brand}</span><Link href={`/product/${product.id}`}><h3>{product.title}</h3></Link><div className="rating"><b>★ {product.rating}</b><span>{product.reviewCount.toLocaleString("en-IN")} reviews</span></div><div className="price"><strong>{money(product.price)}</strong><del>{money(product.originalPrice)}</del><span>{discount}% off</span></div><p className="delivery">{product.delivery}</p><button className={`add-btn ${added?"added":""}`} onClick={async()=>{try{await addToCart(product);setAdded(true)}catch{}}} aria-live="polite">{added?"✓ Added to cart":"Add to cart"}</button></div>
  </article>
}
