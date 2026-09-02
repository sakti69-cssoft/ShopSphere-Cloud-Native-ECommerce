export type ProductBadge = "Bestseller" | "New" | "Deal";

export interface Product {
  id: string; title: string; brand: string; category: string; image: string; images: string[];
  rating: number; reviewCount: number; originalPrice: number; price: number; badge?: ProductBadge;
  delivery: string; stock: number; description: string; specs: Record<string, string>;
}
export interface Category { name: string; icon: string; image: string; }
export interface User { id: string; name: string; email: string; phone?: string; }
export interface CartItem { product: Product; quantity: number; saved?: boolean; }
export interface Review { id: string; user: string; rating: number; title: string; body: string; date: string; }
export interface Address { id: string; name: string; line1: string; city: string; state: string; postalCode: string; phone: string; isDefault?: boolean; }
export interface Order { id: string; date: string; status: "Confirmed" | "Shipped" | "Out for delivery" | "Delivered" | "Cancelled"; items: CartItem[]; total: number; address: Address; }
