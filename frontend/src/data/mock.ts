import { Address, Category, Order, Product, Review } from "@/types";

const localImages: Record<string, string> = {
  "photo-1511707171634-5f897ff02aa9": "/media/cat-mobiles.jpg",
  "photo-1496181133206-80ce9b88a853": "/media/product-laptop.jpg",
  "photo-1526738549149-8e07eca6c147": "/media/cat-electronics.jpg",
  "photo-1445205170230-053b83016050": "/media/cat-fashion.jpg",
  "photo-1555041469-a586c61ea9bc": "/media/cat-home.jpg",
  "photo-1606144042614-b2417e99c4e3": "/media/product-console.jpg",
  "photo-1523275335684-37898b6baf30": "/media/product-watch.jpg",
  "photo-1586023492125-27b2c045efd7": "/media/cat-appliances.jpg",
  "photo-1607083206968-13611e3d76db": "/media/cat-deals.jpg",
  "photo-1592899677977-9c10ca588bbd": "/media/product-phone.jpg",
  "photo-1517336714731-489689fd1ca8": "/media/product-laptop.jpg",
  "photo-1505740420928-5e560c06d30e": "/media/product-headphones.jpg",
  "photo-1603252110481-7ba873bf42ab": "/media/product-shirt.jpg",
  "photo-1585515320310-259814833e62": "/media/product-airfryer.jpg",
  "photo-1587829741301-dc798b83add3": "/media/product-keyboard.jpg",
};
const img = (id: string) => localImages[id] ?? "/media/cat-deals.jpg";

export const categories: Category[] = [
  ["Mobiles","▯","/media/cat-mobiles.jpg"], ["Laptops","⌨","/media/cat-laptops.jpg"],
  ["Electronics","◉","/media/cat-electronics.jpg"], ["Fashion","◇","/media/cat-fashion.jpg"],
  ["Home","⌂","/media/cat-home.jpg"], ["Gaming","✦","/media/cat-gaming.jpg"],
  ["Accessories","⌁","/media/cat-accessories.jpg"], ["Appliances","◫","/media/cat-appliances.jpg"],
  ["Deals","%","/media/cat-deals.jpg"],
].map(([name,icon,image]) => ({name,icon,image}));

export const products: Product[] = [
  {id:"nova-x-pro",title:"Nova X Pro 5G, 256GB, Midnight Titanium",brand:"Orion",category:"Mobiles",image:img("photo-1592899677977-9c10ca588bbd"),images:[img("photo-1592899677977-9c10ca588bbd"),img("photo-1511707171634-5f897ff02aa9")],rating:4.7,reviewCount:2846,originalPrice:89999,price:72999,badge:"Bestseller",delivery:"Free delivery by tomorrow",stock:18,description:"Flagship performance, pro-grade cameras and all-day intelligent battery life in an aerospace titanium body.",specs:{Display:"6.7-inch AMOLED 120Hz",Processor:"Apex A18 Pro",Camera:"50MP triple camera",Battery:"5100mAh",Warranty:"1 year"}},
  {id:"aerobook-air",title:"AeroBook Air 14, Ultra 7, 16GB/1TB",brand:"Aster",category:"Laptops",image:img("photo-1496181133206-80ce9b88a853"),images:[img("photo-1496181133206-80ce9b88a853"),img("photo-1517336714731-489689fd1ca8")],rating:4.6,reviewCount:1289,originalPrice:119990,price:94990,badge:"Deal",delivery:"Free delivery in 2 days",stock:9,description:"An ultra-light productivity powerhouse with a vivid OLED display and remarkable battery life.",specs:{Display:"14-inch 2.8K OLED",Processor:"Intel Core Ultra 7",Memory:"16GB LPDDR5X",Storage:"1TB NVMe SSD",Weight:"1.18kg"}},
  {id:"sonic-pulse",title:"SonicPulse Pro Spatial ANC Headphones",brand:"Vox",category:"Electronics",image:img("photo-1505740420928-5e560c06d30e"),images:[img("photo-1505740420928-5e560c06d30e")],rating:4.8,reviewCount:5421,originalPrice:29999,price:19999,badge:"Bestseller",delivery:"Free same-day delivery",stock:42,description:"Studio-grade sound, adaptive noise cancellation and 45-hour listening comfort.",specs:{Audio:"40mm titanium drivers",Battery:"45 hours",Connectivity:"Bluetooth 5.4",Weight:"248g",Warranty:"2 years"}},
  {id:"pulse-watch",title:"Pulse Watch S2 GPS + Cellular, 45mm",brand:"Nexa",category:"Electronics",image:img("photo-1523275335684-37898b6baf30"),images:[img("photo-1523275335684-37898b6baf30")],rating:4.5,reviewCount:976,originalPrice:34999,price:27999,badge:"New",delivery:"Free delivery by tomorrow",stock:24,description:"A refined health companion with advanced sleep, fitness and connectivity features.",specs:{Display:"1.9-inch LTPO OLED",Water:"5 ATM",GPS:"Dual-band",Battery:"36 hours",Sensors:"ECG, SpO₂, temperature"}},
  {id:"vertex-console",title:"Vertex One 1TB Gaming Console Bundle",brand:"Vertex",category:"Gaming",image:img("photo-1606144042614-b2417e99c4e3"),images:[img("photo-1606144042614-b2417e99c4e3")],rating:4.9,reviewCount:3312,originalPrice:54999,price:49999,badge:"Deal",delivery:"Free secure delivery",stock:7,description:"Immersive 4K gaming with ray tracing, ultra-fast storage and a wireless controller.",specs:{Storage:"1TB custom SSD",Resolution:"Up to 4K 120fps",Audio:"3D spatial",Controller:"Wireless haptic",Warranty:"1 year"}},
  {id:"linen-shirt",title:"Italian Linen Relaxed Shirt",brand:"North & Loom",category:"Fashion",image:img("photo-1603252110481-7ba873bf42ab"),images:[img("photo-1603252110481-7ba873bf42ab")],rating:4.4,reviewCount:684,originalPrice:4999,price:2799,badge:"New",delivery:"Delivery in 2 days",stock:31,description:"Breathable premium linen with a contemporary relaxed silhouette.",specs:{Material:"100% European linen",Fit:"Relaxed",Care:"Machine wash cold",Origin:"Made in India",Sizes:"S–XXL"}},
  {id:"chef-airfryer",title:"CrispChef DualZone Smart Air Fryer 9L",brand:"Hearth",category:"Appliances",image:img("photo-1585515320310-259814833e62"),images:[img("photo-1585515320310-259814833e62")],rating:4.6,reviewCount:1950,originalPrice:18999,price:12999,badge:"Deal",delivery:"Free delivery in 2 days",stock:15,description:"Two independent cooking zones, intelligent sync and crisp results with less oil.",specs:{Capacity:"9 litres",Power:"2400W",Programs:"8 presets",Controls:"Digital touch",Warranty:"2 years"}},
  {id:"mechanical-keyboard",title:"K87 Pro Wireless Mechanical Keyboard",brand:"Arc",category:"Accessories",image:img("photo-1587829741301-dc798b83add3"),images:[img("photo-1587829741301-dc798b83add3")],rating:4.7,reviewCount:2234,originalPrice:12999,price:8499,badge:"Bestseller",delivery:"Free delivery by tomorrow",stock:56,description:"A compact gasket-mounted keyboard with hot-swappable switches and tri-mode connectivity.",specs:{Layout:"87-key TKL",Switches:"Tactile, hot-swappable",Battery:"4000mAh",Connection:"2.4G, Bluetooth, USB-C",Lighting:"Per-key RGB"}},
];

export const reviews: Review[] = [
  {id:"r1",user:"Aarav M.",rating:5,title:"Exceeded expectations",body:"Premium build, fast delivery and performance that easily lasts through a full workday.",date:"12 Aug 2026"},
  {id:"r2",user:"Meera S.",rating:4,title:"Excellent value",body:"Beautifully packaged and exactly as described. Setup was effortless.",date:"4 Aug 2026"},
];
export const address: Address = {id:"a1",name:"Sudarshan Kumar",line1:"24, Lake View Road, Indiranagar",city:"Bengaluru",state:"Karnataka",postalCode:"560038",phone:"+91 98765 43210",isDefault:true};
export const orders: Order[] = [{id:"SS-824196",date:"24 Aug 2026",status:"Shipped",items:[{product:products[2],quantity:1}],total:19999,address},{id:"SS-819402",date:"8 Aug 2026",status:"Delivered",items:[{product:products[7],quantity:1}],total:8499,address}];
export const money = (value:number) => `₹${value.toLocaleString("en-IN")}`;
