"use client";
import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { authApi } from "@/lib/api/auth";
import { onUnauthorized, tokenStore } from "@/lib/api/client";
import type { ApiUser } from "@/lib/api/types";
type Registration={firstName:string;lastName:string;email:string;password:string;phone?:string};
type Value={user:ApiUser|null;loading:boolean;login:(email:string,password:string)=>Promise<ApiUser>;register:(input:Registration)=>Promise<ApiUser>;logout:()=>void};
const Context=createContext<Value|null>(null);
export function AuthProvider({children}:{children:React.ReactNode}){const[user,setUser]=useState<ApiUser|null>(null);const[loading,setLoading]=useState(true);const logout=useCallback(()=>{authApi.logout();setUser(null)},[]);useEffect(()=>{onUnauthorized(logout);if(!tokenStore.access()&&!tokenStore.refresh()){Promise.resolve().then(()=>setLoading(false));return}authApi.me().then(setUser).catch(logout).finally(()=>setLoading(false))},[logout]);const value=useMemo<Value>(()=>({user,loading,login:async(email,password)=>{const result=await authApi.login(email,password);setUser(result.user);return result.user},register:async input=>{await authApi.register(input);const result=await authApi.login(input.email,input.password);setUser(result.user);return result.user},logout}),[user,loading,logout]);return <Context.Provider value={value}>{children}</Context.Provider>}
export function useAuth(){const value=useContext(Context);if(!value)throw new Error("useAuth requires AuthProvider");return value}
