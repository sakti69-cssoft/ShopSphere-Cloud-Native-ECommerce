"use client";

import Link from "next/link";
import { useState } from "react";
import { RequireAuth } from "@/components/RequireAuth";
import { SafeImage } from "@/components/SafeImage";
import { useStore } from "@/context/store";
import { money } from "@/data/mock";

function WishlistContent() {
  const{wishlist,wishlistLoading,moveWishlistToCart,toggleWishlist}=useStore();
  const[message,setMessage]=useState("");
  if(wishlistLoading&&!wishlist.length)return <div className="page-wrap"><div className="state-card" role="status">Loading your saved products…</div></div>;
  return <div className="page-wrap"><div className="page-heading-row"><div><span className="eyebrow">Saved for you</span><h1 className="page-title">Your wishlist <small>({wishlist.length})</small></h1></div>{message&&<p className="success-note" role="status">{message}</p>}</div>{wishlist.length?<div className="wishlist-grid">{wishlist.map(product=><article className="wishlist-card" key={product.id}><Link className="wishlist-image" href={`/product/${product.id}`}><SafeImage src={product.image} alt={product.title} fill sizes="220px" fallbackLabel={product.title} category={product.category}/></Link><div><span className="brand">{product.brand}</span><Link href={`/product/${product.id}`}><h2>{product.title}</h2></Link><div className="rating"><b>★ {product.rating.toFixed(1)}</b><span>{product.reviewCount} reviews</span></div><strong className="wishlist-price">{money(product.price)}</strong><div className="wishlist-actions"><button className="btn-primary" disabled={wishlistLoading} onClick={async()=>{try{await moveWishlistToCart(product);setMessage(`${product.title} moved to your cart.`)}catch{setMessage("Could not move this item. Please retry.")}}}>Move to cart</button><button className="btn-quiet" disabled={wishlistLoading} onClick={async()=>{try{await toggleWishlist(product);setMessage(`${product.title} removed.`)}catch{setMessage("Could not remove this item.")}}}>Remove</button></div></div></article>)}</div>:<div className="empty"><div className="symbol">♡</div><h2>Save the things you love</h2><p>Tap the heart on any product and it will stay here across sign-ins.</p><Link className="btn-primary" href="/search">Discover products</Link></div>}</div>;
}

export default function Wishlist() { return <RequireAuth><WishlistContent/></RequireAuth>; }
