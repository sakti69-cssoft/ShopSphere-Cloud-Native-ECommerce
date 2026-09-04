import {apiRequest} from "./client";
import type {ApiSavedAddress} from "./types";

export type AddressInput={
 recipientName:string;phone:string;line1:string;line2?:string;city:string;state:string;
 postalCode:string;country:string;defaultAddress:boolean;
};

export const addressApi={
 list:()=>apiRequest<ApiSavedAddress[]>("/auth/addresses"),
 get:(id:string)=>apiRequest<ApiSavedAddress>(`/auth/addresses/${id}`),
 add:(input:AddressInput)=>apiRequest<ApiSavedAddress>("/auth/addresses",{method:"POST",body:JSON.stringify(input)}),
 update:(id:string,input:AddressInput)=>apiRequest<ApiSavedAddress>(`/auth/addresses/${id}`,{method:"PUT",body:JSON.stringify(input)}),
 setDefault:(id:string)=>apiRequest<ApiSavedAddress>(`/auth/addresses/${id}/default`,{method:"PUT"}),
 remove:(id:string)=>apiRequest<void>(`/auth/addresses/${id}`,{method:"DELETE"})
};
