"use client";import { usePathname } from "next/navigation";import { Header } from "./Header";import { Footer } from "./Footer";
export function AppShell({children}:{children:React.ReactNode}){const admin=usePathname().startsWith("/admin");return admin?<>{children}</>:<><Header/><main>{children}</main><Footer/></>}
