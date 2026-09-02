"use client";
import { Suspense,useEffect,useState } from "react";
import { useSearchParams } from "next/navigation";
import { ProductCard } from "@/components/ProductCard";
import { productApi } from "@/lib/api/products";
import { toProduct } from "@/lib/api/mappers";
import type { Product } from "@/types";
function Results(){
 const params=useSearchParams();const[sort,setSort]=useState("createdAt,desc"),[items,setItems]=useState<Product[]>([]),[loading,setLoading]=useState(true),[error,setError]=useState("");const q=params.get("q")||undefined,category=params.get("category")||undefined;
 useEffect(()=>{let current=true;Promise.resolve().then(()=>{if(current){setLoading(true);setError("")}});productApi.list({q,category,sort,size:24}).then(p=>{if(current)setItems(p.content.map(toProduct))}).catch(e=>{if(current)setError(e instanceof Error?e.message:"Catalog unavailable")}).finally(()=>{if(current)setLoading(false)});return()=>{current=false}},[q,category,sort]);
 return <div className="page-wrap search-page"><div className="breadcrumb">Home / Catalog</div><h1 className="page-title">{q?`Results for “${q}”`:category||"Explore all products"}</h1><div className="results-top"><span>{loading?"Loading…":`${items.length} products`}</span><select value={sort} onChange={e=>setSort(e.target.value)} aria-label="Sort products"><option value="createdAt,desc">Newest first</option><option value="price,asc">Price: Low to high</option><option value="price,desc">Price: High to low</option><option value="rating,desc">Customer rating</option></select></div>{error?<div className="state-card" role="alert"><h2>Catalog unavailable</h2><p>{error}</p><button className="btn-primary" onClick={()=>location.reload()}>Retry</button></div>:loading?<div className="state-card" role="status">Loading products…</div>:items.length?<div className="results-grid">{items.map(p=><ProductCard key={p.id} product={p}/>)}</div>:<div className="empty"><div className="symbol">⌕</div><h2>No matching products</h2><p>Try a broader search or explore all categories.</p></div>}</div>
}
export default function SearchPage(){return <Suspense fallback={<div className="page-wrap"><div className="state-card">Loading products…</div></div>}><Results/></Suspense>}
