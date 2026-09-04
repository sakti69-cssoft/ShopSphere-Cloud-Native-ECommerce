"use client";

import { createContext,useCallback,useContext,useEffect,useMemo,useRef,useState } from "react";
import { useAuth } from "@/context/auth";
import { cartApi } from "@/lib/api/cart";
import { productApi } from "@/lib/api/products";
import { wishlistApi } from "@/lib/api/wishlist";
import { toProduct } from "@/lib/api/mappers";
import type { ApiCart } from "@/lib/api/types";
import type { CartItem,Product } from "@/types";

const GUEST_CART="ss-cart",LEGACY_WISHLIST="ss-wishlist";
type Store={cart:CartItem[];wishlist:Product[];cartCount:number;busy:boolean;wishlistLoading:boolean;error:string|null;addToCart:(p:Product,quantity?:number)=>Promise<void>;updateQty:(id:string,n:number)=>Promise<void>;removeFromCart:(id:string)=>Promise<void>;toggleWishlist:(p:Product)=>Promise<void>;moveWishlistToCart:(p:Product)=>Promise<void>;inWishlist:(id:string)=>boolean;clearCart:()=>Promise<void>;refreshCart:()=>Promise<void>;refreshWishlist:()=>Promise<void>};
const Context=createContext<Store|null>(null);
const fallback=(i:ApiCart["items"][number]):Product=>({id:i.productId,title:i.productName,brand:"ShopSphere",category:"",image:i.imageUrl||"/media/cat-deals.jpg",images:[i.imageUrl||"/media/cat-deals.jpg"],rating:0,reviewCount:0,originalPrice:i.unitPrice,price:i.unitPrice,delivery:"Delivery details shown at checkout",stock:0,description:"",specs:{}});

export function StoreProvider({children}:{children:React.ReactNode}) {
 const{user,loading:authLoading}=useAuth();
 const[cart,setCart]=useState<CartItem[]>([]),[wishlist,setWishlist]=useState<Product[]>([]);
 const[ready,setReady]=useState(false),[busy,setBusy]=useState(false),[wishlistLoading,setWishlistLoading]=useState(false);
 const[error,setError]=useState<string|null>(null);
 const merged=useRef<string|null>(null),wishlistUser=useRef<string|null>(null);

 useEffect(()=>{Promise.resolve().then(()=>{try{setCart(JSON.parse(localStorage.getItem(GUEST_CART)||"[]"))}catch{localStorage.removeItem(GUEST_CART)}finally{setReady(true)}})},[]);
 useEffect(()=>{if(ready&&!user&&!authLoading&&merged.current===null)localStorage.setItem(GUEST_CART,JSON.stringify(cart))},[cart,ready,user,authLoading]);

 const hydrate=useCallback(async(source:ApiCart)=>{setCart(await Promise.all(source.items.map(async item=>{try{return{product:toProduct(await productApi.get(item.productId)),quantity:item.quantity}}catch{return{product:fallback(item),quantity:item.quantity}}})))},[]);
 const refreshCart=useCallback(async()=>{if(!user)return;setBusy(true);try{setError(null);await hydrate(await cartApi.get(user.id))}catch(e){setError(e instanceof Error?e.message:"Could not load your cart.")}finally{setBusy(false)}},[hydrate,user]);
 const refreshWishlist=useCallback(async()=>{if(!user){setWishlist([]);return}setWishlistLoading(true);try{setError(null);setWishlist((await wishlistApi.list()).map(toProduct))}catch(e){setError(e instanceof Error?e.message:"Could not load your wishlist.")}finally{setWishlistLoading(false)}},[user]);

 useEffect(()=>{if(authLoading||!ready)return;if(!user){if(merged.current){merged.current=null;Promise.resolve().then(()=>setCart([]));localStorage.removeItem(GUEST_CART)}wishlistUser.current=null;Promise.resolve().then(()=>setWishlist([]));return}if(merged.current===user.id)return;merged.current=user.id;const guest:CartItem[]=JSON.parse(localStorage.getItem(GUEST_CART)||"[]");setBusy(true);setError(null);(async()=>{try{for(const item of guest)await cartApi.add(user.id,item.product.id,item.quantity);localStorage.removeItem(GUEST_CART);await hydrate(await cartApi.get(user.id))}catch(e){setError(e instanceof Error?e.message:"Could not synchronize your cart.")}finally{setBusy(false)}})();
 // This transition intentionally runs once for each authenticated user.
 // eslint-disable-next-line react-hooks/exhaustive-deps
 },[authLoading,ready,user?.id,hydrate]);

 useEffect(()=>{if(authLoading||!ready)return;if(!user)return;if(wishlistUser.current===user.id)return;wishlistUser.current=user.id;setWishlistLoading(true);(async()=>{try{const legacy:Product[]=JSON.parse(localStorage.getItem(LEGACY_WISHLIST)||"[]");for(const product of legacy)await wishlistApi.add(product.id);localStorage.removeItem(LEGACY_WISHLIST);setWishlist((await wishlistApi.list()).map(toProduct))}catch(e){setError(e instanceof Error?e.message:"Could not synchronize your wishlist.")}finally{setWishlistLoading(false)}})()},[authLoading,ready,user]);

 const addToCart=useCallback(async(p:Product,quantity=1)=>{if(quantity<1)throw new Error("Quantity must be at least one.");if(!user){setCart(current=>current.some(item=>item.product.id===p.id)?current.map(item=>item.product.id===p.id?{...item,quantity:item.quantity+quantity}:item):[...current,{product:p,quantity}]);return}setBusy(true);try{setError(null);await hydrate(await cartApi.add(user.id,p.id,quantity))}catch(e){setError(e instanceof Error?e.message:"Could not add this item.");throw e}finally{setBusy(false)}},[hydrate,user]);
 const updateQty=useCallback(async(id:string,n:number)=>{if(!user){setCart(current=>n<1?current.filter(item=>item.product.id!==id):current.map(item=>item.product.id===id?{...item,quantity:n}:item));return}setBusy(true);try{await hydrate(n<1?await cartApi.remove(user.id,id):await cartApi.update(user.id,id,n))}finally{setBusy(false)}},[hydrate,user]);
 const removeFromCart=useCallback(async(id:string)=>{if(!user){setCart(current=>current.filter(item=>item.product.id!==id));return}setBusy(true);try{await hydrate(await cartApi.remove(user.id,id))}finally{setBusy(false)}},[hydrate,user]);
 const clearCart=useCallback(async()=>{if(user)await cartApi.clear(user.id);setCart([])},[user]);
 const toggleWishlist=useCallback(async(p:Product)=>{if(!user)throw new Error("Sign in to save products to your wishlist.");setWishlistLoading(true);try{setError(null);if(wishlist.some(item=>item.id===p.id)){await wishlistApi.remove(p.id);setWishlist(current=>current.filter(item=>item.id!==p.id))}else{await wishlistApi.add(p.id);setWishlist(current=>current.some(item=>item.id===p.id)?current:[p,...current])}}catch(e){setError(e instanceof Error?e.message:"Could not update your wishlist.");throw e}finally{setWishlistLoading(false)}},[user,wishlist]);
 const moveWishlistToCart=useCallback(async(p:Product)=>{await addToCart(p);if(user){await wishlistApi.remove(p.id);setWishlist(current=>current.filter(item=>item.id!==p.id))}},[addToCart,user]);
 const value=useMemo<Store>(()=>({cart,wishlist,cartCount:cart.reduce((n,item)=>n+item.quantity,0),busy,wishlistLoading,error,addToCart,updateQty,removeFromCart,toggleWishlist,moveWishlistToCart,inWishlist:id=>wishlist.some(product=>product.id===id),clearCart,refreshCart,refreshWishlist}),[cart,wishlist,busy,wishlistLoading,error,addToCart,updateQty,removeFromCart,toggleWishlist,moveWishlistToCart,clearCart,refreshCart,refreshWishlist]);
 return <Context.Provider value={value}>{children}</Context.Provider>;
}

export const useStore=()=>{const value=useContext(Context);if(!value)throw new Error("useStore requires StoreProvider");return value};
