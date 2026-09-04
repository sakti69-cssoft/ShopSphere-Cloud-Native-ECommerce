import { apiRequest } from "./client";
import type { ApiProduct } from "./types";

export const wishlistApi={
  list:()=>apiRequest<ApiProduct[]>("/products/wishlist"),
  add:(productId:string)=>apiRequest<ApiProduct>(`/products/wishlist/${productId}`,{method:"POST"}),
  remove:(productId:string)=>apiRequest<void>(`/products/wishlist/${productId}`,{method:"DELETE"}),
};
