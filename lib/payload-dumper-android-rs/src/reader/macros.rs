#[macro_export]
macro_rules! read_le {
    ($b:expr, u16) => {{ u16::from_le_bytes($b.try_into().expect("Can't get u16")) }};
    ($b:expr, u32) => {{ u32::from_le_bytes($b.try_into().expect("Can't get u32")) }};
    ($b:expr, u64) => {{ u64::from_le_bytes($b.try_into().expect("Can't get u64")) }};
}

#[macro_export]
macro_rules! read_be {
    ($r:expr, u64) => {{
        let mut buf = [0; 8];
        $r.read_exact(&mut buf)?;
        u64::from_be_bytes(buf)
    }};
    ($r:expr, u32) => {{
        let mut buf = [0; 4];
        $r.read_exact(&mut buf)?;
        u32::from_be_bytes(buf)
    }};
    ($r:expr, u16) => {{
        let mut buf = [0; 2];
        $r.read_exact(&mut buf)?;
        u16::from_be_bytes(buf)
    }};
    ($r:expr, [u8; $n:expr]) => {{
        let mut buf = [0; $n];
        $r.read_exact(&mut buf)?;
        buf
    }};
}
