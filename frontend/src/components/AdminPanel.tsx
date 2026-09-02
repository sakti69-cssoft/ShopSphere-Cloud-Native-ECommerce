"use client";
import {useCallback,useEffect,useState,type FormEvent} from "react";
import {AdminShell} from "./AdminShell";
import {SafeImage} from "./SafeImage";
import {apiRequest} from "@/lib/api/client";
import {productApi} from "@/lib/api/products";
import {inventoryApi} from "@/lib/api/inventory";
import {orderApi} from "@/lib/api/orders";
import {productImage} from "@/lib/product-images";
import type {ApiProduct,ApiPage,ApiOrder,ApiInventory,OrderStatus} from "@/lib/api/types";

type Kind="overview"|"products"|"inventory"|"orders"|"customers";
const transitions:Record<OrderStatus,OrderStatus[]>={PENDING:["CONFIRMED","CANCELLED"],CONFIRMED:["PROCESSING","CANCELLED"],PROCESSING:["SHIPPED","CANCELLED"],SHIPPED:["DELIVERED"],DELIVERED:[],CANCELLED:[]};
const blank={sku:"",name:"",slug:"",description:"",brand:"",category:"Electronics",price:1,originalPrice:1,discountPercentage:0,rating:0,reviewCount:0,imageUrls:["/media/cat-electronics.jpg"],specifications:{},active:true};
export function AdminPanel({kind}:{kind:Kind}) {
 const [products,setProducts]=useState<ApiProduct[]>([]),[orders,setOrders]=useState<ApiOrder[]>([]),[stock,setStock]=useState<Record<string,ApiInventory>>({});
 const [page,setPage]=useState(0),[more,setMore]=useState(false),[busy,setBusy]=useState(false),[error,setError]=useState(""),[message,setMessage]=useState("");
 const [draft,setDraft]=useState<typeof blank|null>(null),[editing,setEditing]=useState<string|null>(null);
 const load=useCallback(async()=>{
  if(kind==="customers")return;
  setBusy(true);setError("");
  try {
   if(kind==="orders"){const rows=await apiRequest<ApiOrder[]>(`/orders?page=${page}&size=20`);setOrders(rows);setMore(rows.length===20);}
   else {const result=await apiRequest<ApiPage<ApiProduct>>(`/products/admin?page=${page}&size=20`);setProducts(result.content);setMore(page+1<result.totalPages);
    if(kind==="inventory"){const pairs=await Promise.all(result.content.map(async p=>{try{return [p.id,await inventoryApi.get(p.id)] as const}catch(e){if(e instanceof Error&&"status" in e&&e.status===404)return null;throw e;}}));setStock(Object.fromEntries(pairs.filter(p=>p!==null)));}
   }
  }catch(e){setError(e instanceof Error?e.message:"Unable to load admin data");}finally{setBusy(false);}
 },[kind,page]);
 useEffect(()=>{let cancelled=false;Promise.resolve().then(()=>{if(!cancelled)void load()});return()=>{cancelled=true}},[load]);
 async function mutate(action:()=>Promise<unknown>){setBusy(true);setError("");setMessage("");try{await action();setMessage("Saved successfully.");await load();}catch(e){setError(e instanceof Error?e.message:"Operation failed");}finally{setBusy(false);}}
 function saveProduct(e:FormEvent){e.preventDefault();if(!draft)return;void mutate(async()=>{if(editing)await productApi.update(editing,draft);else await productApi.create(draft);setDraft(null);setEditing(null);});}
 return <AdminShell title={kind==="overview"?"Administration":kind[0].toUpperCase()+kind.slice(1)}>
  <p>Live API data · administrator access required</p>
  {error&&<p role="alert" className="form-error">{error}</p>}{message&&<p role="status">{message}</p>}
  {kind==="customers"?<div className="panel">Customer browsing is unavailable. No safe administrator customer-list endpoint exists; credentials and token records are never exposed here.</div>:<>
  {kind==="overview"&&<div className="panel"><h2>Manage your marketplace</h2><p>Use Products for catalog changes, Inventory for stock, and Orders for fulfillment. No simulated sales or customer statistics are shown.</p></div>}
  {kind==="products"&&<button className="btn-primary" disabled={busy} onClick={()=>{setEditing(null);setDraft({...blank});}}>Add product</button>}
  {draft&&<form className="panel" onSubmit={saveProduct}><h2>{editing?"Edit":"Create"} product</h2>
   {(["name","sku","slug","description","brand","category"] as const).map(key=><div className="field" key={key}><label htmlFor={key}>{key}</label><input id={key} required value={draft[key]} onChange={e=>setDraft({...draft,[key]:e.target.value})}/></div>)}
   {(["price","originalPrice"] as const).map(key=><div className="field" key={key}><label htmlFor={key}>{key} (INR)</label><input id={key} type="number" min="0.01" step="0.01" required value={draft[key]} onChange={e=>setDraft({...draft,[key]:Number(e.target.value)})}/></div>)}
   <div className="field"><label htmlFor="imageUrl">Image URL or local /media/ path</label><input id="imageUrl" required value={draft.imageUrls[0]} onChange={e=>setDraft({...draft,imageUrls:[e.target.value]})}/></div>
   <label><input type="checkbox" checked={draft.active} onChange={e=>setDraft({...draft,active:e.target.checked})}/> Active</label>
   <div><button className="btn-primary" disabled={busy}>Save product</button> <button type="button" onClick={()=>setDraft(null)}>Cancel</button></div>
  </form>}
  <div style={{overflowX:"auto",marginTop:20}} aria-busy={busy}>
  {kind==="orders"?<table className="admin-table"><thead><tr><th>Order</th><th>Total</th><th>Status</th><th>Action</th></tr></thead><tbody>{orders.map(o=><tr key={o.id}><td>{o.orderNumber}</td><td>₹{o.totalAmount.toLocaleString("en-IN")}</td><td>{o.status}</td><td>{transitions[o.status].map(next=><button key={next} disabled={busy} onClick={()=>void mutate(()=>orderApi.status(o.id,next))}>{next}</button>)}</td></tr>)}</tbody></table>:
  <table className="admin-table"><thead><tr><th>Product</th><th>SKU / state</th><th>{kind==="inventory"?"Stock":"Price"}</th><th>Actions</th></tr></thead><tbody>{products.map(p=><tr key={p.id}><td><div style={{display:"flex",alignItems:"center",gap:12}}><div style={{position:"relative",width:56,height:56,flexShrink:0}}><SafeImage key={p.imageUrls.join()} src={productImage(p)} alt={p.name} category={p.category} fill sizes="56px" style={{objectFit:"contain"}}/></div>{p.name}</div></td><td>{p.sku}<br/>{p.active?"Active":"Inactive"}</td><td>{kind==="inventory"?(stock[p.id]?`${stock[p.id].quantityAvailable} available / ${stock[p.id].quantityReserved} reserved`:"Not initialized"):`₹${p.price.toLocaleString("en-IN")}`}</td><td>
   {kind==="products"&&<><button disabled={busy} onClick={()=>{setEditing(p.id);setDraft({...p});}}>Edit</button> <button disabled={busy} onClick={()=>{if(window.confirm(`Delete ${p.name}?`))void mutate(()=>productApi.remove(p.id));}}>Delete</button></>}
   {kind==="inventory"&&<form onSubmit={e=>{e.preventDefault();const data=new FormData(e.currentTarget);void mutate(()=>inventoryApi.set(p.id,{sku:p.sku,quantityAvailable:Number(data.get("quantity")),reorderLevel:Number(data.get("reorder"))}));}}><label>Available <input aria-label={`Available stock for ${p.name}`} name="quantity" type="number" min={stock[p.id]?.quantityReserved??0} required defaultValue={stock[p.id]?.quantityAvailable??0} key={stock[p.id]?.updatedAt??p.id}/></label><label> Reorder <input aria-label={`Reorder level for ${p.name}`} name="reorder" type="number" min="0" required defaultValue={stock[p.id]?.reorderLevel??5}/></label><button disabled={busy}>Update stock</button></form>}
   </td></tr>)}</tbody></table>}
  </div>
  {!busy&&!products.length&&kind!=="orders"&&<p>No products found.</p>}{!busy&&kind==="orders"&&!orders.length&&<p>No orders found.</p>}
  <div style={{display:"flex",gap:16,marginTop:20}}><button disabled={busy||page===0} onClick={()=>setPage(p=>p-1)}>Previous</button><span>Page {page+1}</span><button disabled={busy||!more} onClick={()=>setPage(p=>p+1)}>Next</button><button disabled={busy} onClick={()=>void load()}>Refresh</button></div>
  </>}
 </AdminShell>;
}
