"use client";
import Link from "next/link";
import { useEffect,useState } from "react";
import { useRouter } from "next/navigation";
import { categories } from "@/data/mock";
import { productApi } from "@/lib/api/products";

export function SearchBox(){
 const router=useRouter(),[q,setQ]=useState(""),[category,setCategory]=useState(""),[suggestions,setSuggestions]=useState<Array<{id:string;name:string;category:string}>>([]),[open,setOpen]=useState(false);
 useEffect(()=>{const value=q.trim();if(value.length<2)return;const timer=setTimeout(()=>productApi.list({q:value,category:category||undefined,size:6}).then(p=>{setSuggestions(p.content.map(x=>({id:x.id,name:x.name,category:x.category})));setOpen(true)}).catch(()=>setSuggestions([])),250);return()=>clearTimeout(timer)},[q,category]);
 function submit(e:React.FormEvent){e.preventDefault();const p=new URLSearchParams();if(q.trim())p.set("q",q.trim());if(category)p.set("category",category);setOpen(false);router.push(`/search?${p}`)}
 return <form className="search search-autocomplete" onSubmit={submit}><label className="sr-only" htmlFor="header-category">Search category</label><select id="header-category" value={category} onChange={e=>setCategory(e.target.value)}><option value="">All categories</option>{categories.slice(0,8).map(c=><option key={c.name}>{c.name}</option>)}</select><label className="sr-only" htmlFor="header-search">Search ShopSphere</label><input id="header-search" value={q} onChange={e=>setQ(e.target.value)} onFocus={()=>setOpen(true)} placeholder="Search products, brands and categories" autoComplete="off"/><button aria-label="Submit search">⌕</button>{open&&q.trim().length>=2&&suggestions.length>0&&<div className="search-suggestions" role="listbox">{suggestions.map(s=><Link key={s.id} href={`/product/${s.id}`} onClick={()=>setOpen(false)}><b>{s.name}</b><small>{s.category}</small></Link>)}</div>}</form>
}
