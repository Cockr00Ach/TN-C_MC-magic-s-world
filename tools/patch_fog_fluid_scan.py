"""Add the nonphysical fog override without replacing any other installed fog logic."""
import struct

U2=lambda n:struct.pack('>H',n)
U4=lambda n:struct.pack('>I',n)
NAME='updateFluidHeightAndDoFluidPushing'
DESC='(Ljava/util/function/Predicate;)V'

def patch(data):
    assert data[:4]==b'\xca\xfe\xba\xbe'
    at=10;count=struct.unpack_from('>H',data,8)[0];utf={};i=1
    while i<count:
        tag=data[at];at+=1
        if tag==1:
            n=struct.unpack_from('>H',data,at)[0];at+=2
            utf[i]=data[at:at+n].decode('utf-8',errors='replace');at+=n
        elif tag in (3,4):at+=4
        elif tag in (5,6):at+=8;i+=1
        elif tag in (7,8,16,19,20):at+=2
        elif tag in (9,10,11,12,17,18):at+=4
        elif tag==15:at+=3
        else:raise ValueError(tag)
        i+=1
    cp_end=at;at+=6
    n=struct.unpack_from('>H',data,at)[0];at+=2+n*2
    def member_end(start):
        n=struct.unpack_from('>H',data,start+6)[0];pos=start+8
        for _ in range(n):
            size=struct.unpack_from('>I',data,pos+2)[0];pos+=6+size
        return pos
    fields=struct.unpack_from('>H',data,at)[0];at+=2
    for _ in range(fields):at=member_end(at)
    method_count_at=at;methods=struct.unpack_from('>H',data,at)[0];at+=2;kept=[]
    for _ in range(methods):
        end=member_end(at)
        name,desc=struct.unpack_from('>HH',data,at+2)
        if (utf[name],utf[desc])!=(NAME,DESC):kept.append(data[at:end])
        at=end
    # public void override(Predicate): RETURN; no world traversal, fields or dependencies.
    additions=b''.join(b'\x01'+U2(len(s))+s.encode() for s in (NAME,DESC,'Code'))
    code=U2(0)+U2(2)+U4(1)+b'\xb1'+U2(0)+U2(0)
    method=U2(1)+U2(count)+U2(count+1)+U2(1)+U2(count+2)+U4(len(code))+code
    return (data[:8]+U2(count+3)+data[10:cp_end]+additions+
            data[cp_end:method_count_at]+U2(len(kept)+1)+b''.join(kept)+method+data[at:])
