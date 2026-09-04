"use client";
import Link from "next/link";
import { useState } from "react";
import { categories } from "@/data/mock";
import { useStore } from "@/context/store";
import { useAuth } from "@/context/auth";
import { SearchBox } from "@/components/SearchBox";

function LineIcon({name}:{name:"pin"|"user"|"box"|"heart"|"bag"|"search"|"menu"}){
 const paths={pin:<><path d="M12 21s6-5.1 6-11a6 6 0 1 0-12 0c0 5.9 6 11 6 11Z"/><circle cx="12" cy="10" r="2"/></>,user:<><circle cx="12" cy="8" r="3.5"/><path d="M5 20c.7-4 3.1-6 7-6s6.3 2 7 6"/></>,box:<><path d="m4 7 8-4 8 4-8 4-8-4Z"/><path d="M4 7v10l8 4 8-4V7M12 11v10"/></>,heart:<path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8l1.1 1.1L12 21l7.8-7.5 1.1-1.1a5.5 5.5 0 0 0-.1-7.8Z"/>,bag:<><path d="M5 8h14l-1 13H6L5 8Z"/><path d="M9 9V6a3 3 0 0 1 6 0v3"/></>,search:<><circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 5 5"/></>,menu:<><path d="M4 7h16M4 12h16M4 17h16"/></>};
 return <svg aria-hidden viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">{paths[name]}</svg>
}

export function Header(){const {cartCount,wishlist}=useStore();const {user}=useAuth();const [open,setOpen]=useState(false);return <header className="header">
 <div className="topbar"><span>Complimentary delivery over ₹999</span><span>Easy 7-day returns</span><span>100% secure checkout</span></div>
 <div className="header-main"><button className="mobile-toggle" onClick={()=>setOpen(!open)} aria-expanded={open} aria-controls="category-navigation" aria-label="Toggle navigation"><LineIcon name="menu"/></button><Link className="logo" href="/" aria-label="ShopSphere home"><b>SHOP</b><i>SPHERE</i></Link>
 <Link className="location" href={user?"/account/addresses":"/login?returnTo=%2Faccount%2Faddresses"} aria-label="Choose a saved delivery address"><LineIcon name="pin"/><span><small>Deliver to</small><strong>{user?"Saved address":"Set address"}</strong></span></Link>
 <SearchBox/>
 <nav className="actions" aria-label="Account actions"><Link href={user?"/account":"/login"}><LineIcon name="user"/><span><small>{user?`Hello, ${user.firstName}`:"Hello, sign in"}</small><b>Account</b></span></Link><Link href="/orders"><LineIcon name="box"/><span><small>Track & manage</small><b>Orders</b></span></Link><Link href="/wishlist"><span className="action-glyph"><LineIcon name="heart"/><em>{wishlist.length}</em></span><span className="action-label">Wishlist</span></Link><Link href="/cart"><span className="action-glyph"><LineIcon name="bag"/><em>{cartCount}</em></span><span className="action-label">Cart</span></Link></nav></div>
 <nav id="category-navigation" className={`category-nav ${open?"open":""}`} aria-label="Product categories"><Link className="all-link" href="/search"><LineIcon name="menu"/> All</Link>{categories.slice(0,8).map(c=><Link key={c.name} href={`/search?category=${c.name}`} onClick={()=>setOpen(false)}>{c.name}</Link>)}<Link className="nav-deal" href="/search?badge=Deal">Sphere Sale <span>Up to 60% off</span></Link></nav>
 </header>}
