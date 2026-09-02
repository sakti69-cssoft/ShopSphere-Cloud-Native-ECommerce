"use client";
import Image,{type ImageProps}from"next/image";import{useState}from"react";import{categoryFallback}from"@/lib/product-images";
type Props=ImageProps&{fallbackLabel?:string;category?:string;fallbackSrc?:string};
function allowed(src:ImageProps["src"]){if(typeof src!=="string")return true;if(src.startsWith("/media/"))return true;try{const url=new URL(src);return url.protocol==="https:"&&url.hostname==="images.unsplash.com"&&!url.port&&!url.username&&!url.password;}catch{return false;}}
export function SafeImage(props:Props){return <ImageAttempt key={typeof props.src==="string"?props.src:props.alt} {...props}/>;}
function ImageAttempt({fallbackLabel="ShopSphere product",category,fallbackSrc,alt,onError,...props}:Props){const[attempt,setAttempt]=useState(0);const fallback=fallbackSrc&&allowed(fallbackSrc)?fallbackSrc:categoryFallback(category);if(attempt>1)return <span className="media-fallback" role="img" aria-label={`${fallbackLabel} image unavailable`}><span>SS</span><small>{category||"Product"}</small></span>;return <Image {...props} src={attempt===0&&allowed(props.src)?props.src:fallback} alt={alt} onError={event=>{setAttempt(value=>value+1);onError?.(event)}}/>}
