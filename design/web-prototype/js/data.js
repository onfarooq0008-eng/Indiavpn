/* Static demo data. Replaced by backend responses once the API is live. */
window.VPN_DATA = {
  servers: [
    { id: "in-mum", name: "Mumbai",        country: "India", code: "IN", ping: 18,  bars: 4, tags: ["streaming", "gaming"] },
    { id: "in-del", name: "New Delhi",     country: "India", code: "IN", ping: 24,  bars: 4, tags: ["streaming"] },
    { id: "in-blr", name: "Bangalore",     country: "India", code: "IN", ping: 31,  bars: 3, tags: ["gaming"] },
    { id: "in-maa", name: "Chennai",       country: "India", code: "IN", ping: 36,  bars: 3, tags: [] },
    { id: "in-hyd", name: "Hyderabad",     country: "India", code: "IN", ping: 29,  bars: 4, tags: ["streaming"] },
    { id: "in-ccu", name: "Kolkata",       country: "India", code: "IN", ping: 42,  bars: 2, tags: [] },
    { id: "sg-sin", name: "Singapore",     country: "Singapore", code: "SG", color: "#E5484D", ping: 52,  bars: 3, tags: ["gaming", "streaming"] },
    { id: "us-nyc", name: "United States", country: "United States", code: "US", color: "#3E63DD", ping: 128, bars: 2, tags: ["streaming"] }
  ],
  plans: [
    { id: "1m",  title: "1 month",   sub: "Billed monthly",          price: 149 },
    { id: "12m", title: "12 months", sub: "₹49 a month · Save 67%",  price: 599, best: true },
    { id: "6m",  title: "6 months",  sub: "₹58 a month · Save 61%",  price: 349 }
  ],
  apps: [
    { id: "phonepe",  name: "PhonePe",   letter: "P", color: "#5F259F", bypass: true },
    { id: "gpay",     name: "Google Pay", letter: "G", color: "#3E63DD", bypass: true },
    { id: "paytm",    name: "Paytm",     letter: "P", color: "#00B9F1", bypass: false },
    { id: "whatsapp", name: "WhatsApp",  letter: "W", color: "#25D366", bypass: false },
    { id: "chrome",   name: "Chrome",    letter: "C", color: "#E5484D", bypass: false },
    { id: "youtube",  name: "YouTube",   letter: "Y", color: "#FF0000", bypass: false },
    { id: "instagram",name: "Instagram", letter: "I", color: "#C13584", bypass: false }
  ],
  onboarding: [
    { title: "Private and secure", text: "Strong encryption keeps your browsing, banking and chats safe on any network.",
      icon: '<rect x="14" y="32" width="44" height="30" rx="8"/><path d="M24 32V24a12 12 0 0 1 24 0v8"/><circle cx="36" cy="47" r="3"/>' },
    { title: "Servers across India", text: "Connect to fast servers in Mumbai, Delhi, Bangalore, Chennai and more than 50 countries.",
      icon: '<circle cx="36" cy="36" r="24"/><ellipse cx="36" cy="36" rx="10" ry="24"/><path d="M12 36h48"/>' },
    { title: "Fast, with no limits", text: "Unlimited bandwidth and smart routing for smooth streaming and gaming.",
      icon: '<path d="M40 8 18 40h16l-4 24 24-34H38z"/>' }
  ]
};
