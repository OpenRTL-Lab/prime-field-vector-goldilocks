/*
 * OpenRTL-Header-Begin
 * OpenRTL — Open-source hardware accelerators.
 * Organization: https://github.com/OpenRTL-Lab
 * Website: https://openrtl.top/
 *
 * Project: Goldilocks Prime-Field Vector Hardware Accelerator
 * Chisel hardware for vector arithmetic and dot products over the Goldilocks field.
 * Repository: https://github.com/OpenRTL-Lab/prime-field-vector-goldilocks
 * File: operators/prime-field-vector/cmodel/reference.c
 *
 * SPDX-FileCopyrightText: 2026 Goldilocks Prime Field Vector contributors
 * Copyright applies to original project contributions only.
 * SPDX-License-Identifier: MIT
 * See LICENSE and applicable third-party notices.
 * OpenRTL-Header-End
 */

#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <inttypes.h>
#include <string.h>
static const uint64_t P=UINT64_C(0xffffffff00000001);
static FILE *fi,*fo,*ft,*fj;
static uint64_t rng=0x761592381234beefULL;
static unsigned tag=0; static uint64_t inputs=0,outputs=0;
static uint64_t random_field(void){rng^=rng<<13;rng^=rng>>7;rng^=rng<<17;return rng%P;}
static uint64_t value(unsigned i){switch(i%17){case 0:return 0;case 1:return 1;case 2:return P-1;case 3:return P-2;case 4:return 0xffffffff; default:return random_field();}}
static uint64_t mul(uint64_t a,uint64_t b){return (uint64_t)((__uint128_t)a*b%P);}
static uint64_t add(uint64_t a,uint64_t b){return (uint64_t)(((__uint128_t)a+b)%P);}
static void out(uint64_t v){fprintf(fo,"%016" PRIx64 "\n",v);outputs++;}
static void record(uint64_t a,uint64_t b,uint64_t c,unsigned coeff,unsigned last,unsigned count){
  unsigned flags=coeff|(last<<1)|(count<<2);
  fprintf(fi,"%016x%016" PRIx64 "%016" PRIx64 "%016" PRIx64 "\n",flags,c,b,a);inputs++;
}
static void frame(unsigned m,unsigned l,unsigned k,unsigned fault,unsigned bench,unsigned maxl,unsigned maxk){
  unsigned invalid=m>5||l>maxl||(m==4?(k==0||k>maxk):k!=0);
  unsigned status=invalid?1:((fault==5||fault==7||fault==8||fault==9)?3:(fault?2:0));
  unsigned ni=invalid?0:(m==4?k+l*k:l),no=invalid?0:(m==5?1:l);
  __uint128_t metadata=m|((__uint128_t)l<<3)|((__uint128_t)k<<35)|((__uint128_t)ni<<51)|((__uint128_t)no<<83)|((__uint128_t)status<<115);
  fprintf(ft,"%016" PRIx64 "%016" PRIx64 "\n",(uint64_t)(metadata>>64),(uint64_t)metadata);
  fprintf(fj,"%s{\"tag\":%u,\"mode\":%u,\"length\":%u,\"terms\":%u,\"input_count\":%u,\"output_count\":%u,\"status\":%u,\"benchmark\":%s,\"input_offset\":%" PRIu64 ",\"output_offset\":%" PRIu64 "}",tag?",\n":"",tag,m,l,k,ni,no,status,bench?"true":"false",inputs,outputs);tag++;
  if(invalid)return;
  uint64_t lambda[8]={0},acc=0;
  if(m==4)for(unsigned j=0;j<k;j++){uint64_t raw=(fault==9&&j==0)?P:value(j+2);lambda[j]=raw>=P?0:raw;record(raw,0,0,1,(j==k-1)^(fault==6&&j==0),1);}
  for(unsigned i=0;i<l;i++){
    uint64_t linear=0;
    unsigned terms=m==4?k:1;
    for(unsigned j=0;j<terms;j++){
      unsigned n=i*terms+j;uint64_t a=value(n),b=value(n+2),c=value(n+3);
      if(fault==5&&n==0)a=P;
      if(fault==7&&n==0)b=P;
      if(fault==8&&n==0)c=P;
      if(fault==10&&n==0)a=P;
      unsigned last=i==l-1&&j==terms-1;
      if((fault==1||fault==10)&&n==0)last=!last;
      if(fault==2&&last)last=0;
      record(a,b,c,(fault==3&&n==0),last,(fault==4&&n==0)?0:1);
      if(a>=P)a=0;
      if(b>=P)b=0;
      if(c>=P)c=0;
      if(m==4)linear=add(linear,mul(a,lambda[j]));
      else if(m==5)acc=add(acc,mul(a,b));
      else {uint64_t v= m==0?add(a,b):m==1?(uint64_t)(((__uint128_t)a+P-b)%P):m==2?mul(a,b):add(mul(a,b),c);out(v);}
    }
    if(m==4)out(linear);
  }
  if(m==5)out(acc);
}
int main(int argc,char **argv){
  if(argc!=5)return 2;
  unsigned maxl=atoi(argv[2]),maxk=atoi(argv[3]),batch=atoi(argv[4]);char path[4096];
  #define OPEN(f,n) snprintf(path,sizeof(path),"%s/%s",argv[1],n); f=fopen(path,"w");if(!f)return 3
  OPEN(fi,"input.hex");OPEN(fo,"expected.hex");OPEN(ft,"tasks.hex");OPEN(fj,"tasks.json");fputs("[\n",fj);
  for(unsigned m=0;m<6;m++)for(unsigned t=0;t<4;t++){unsigned l=t==0?0:t==1?1:t==2?7:17;if(l>maxl)l=maxl;frame(m,l,m==4?(t%2?1:maxk):0,0,0,maxl,maxk);}
  frame(6,0,0,0,0,maxl,maxk);frame(0,maxl+1,0,0,0,maxl,maxk);frame(4,1,0,0,0,maxl,maxk);frame(4,1,maxk+1,0,0,maxl,maxk);frame(1,1,1,0,0,maxl,maxk);
  for(unsigned f=1;f<=5;f++)for(unsigned m=0;m<6;m++)frame(m,7,m==4?maxk:0,f,0,maxl,maxk);
  frame(4,3,maxk,6,0,maxl,maxk);
  for(unsigned m=0;m<6;m++)if(m!=4)frame(m,7,0,7,0,maxl,maxk);
  frame(3,7,0,8,0,maxl,maxk);frame(4,7,maxk,9,0,maxl,maxk);frame(3,7,0,10,0,maxl,maxk);
  for(unsigned m=0;m<6;m++)for(unsigned t=0;t<batch;t++)frame(m,maxl,m==4?maxk:0,0,1,maxl,maxk);
  fputs("\n]\n",fj);fclose(fi);fclose(fo);fclose(ft);fclose(fj);
  printf("{\"tasks\":%u,\"inputs\":%" PRIu64 ",\"outputs\":%" PRIu64 "}\n",tag,inputs,outputs);return 0;
}
