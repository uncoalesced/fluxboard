# Engineered by uncoalesced
"""Build the FLCT binary prefix trie for names, Indian vocabulary, and cultural terms.

Shipped as keyboard-core/src/main/assets/names_dict.bin.

Provides:
- Top global given names and surnames
- Top Indian first names and surnames across North, South, East, and West India
- Indian states, major cities, towns, and districts
- Indian cultural terminology, foods, ingredients, and everyday vocabulary
- Foundational multilingual/transliteration vocabulary

Words present here act as an Inviolable Lexicon Shield in the Bloom Engine,
preventing legitimate names and cultural terms from being mangled by autocorrect,
while offering them prominently in completion suggestions.
"""

import math
import struct
import sys
import os

# ---------------------------------------------------------------------------
# Curation: High-value names, surnames, places, and Indian cultural vocabulary
# ---------------------------------------------------------------------------

GLOBAL_NAMES = [
    # Top Western/Global Given Names
    "james", "john", "robert", "michael", "william", "david", "richard", "joseph",
    "thomas", "charles", "christopher", "daniel", "matthew", "anthony", "mark",
    "donald", "steven", "paul", "andrew", "joshua", "kenneth", "kevin", "brian",
    "george", "edward", "ronald", "timothy", "jason", "jeffrey", "ryan", "jacob",
    "gary", "nicholas", "eric", "jonathan", "stephen", "larry", "justin", "scott",
    "brandon", "benjamin", "samuel", "gregory", "alexander", "patrick", "frank",
    "raymond", "jack", "dennis", "jerry", "tyler", "aaron", "jose", "adam", "nathan",
    "henry", "douglas", "zachary", "peter", "kyle", "walter", "ethan", "jeremy",
    "harold", "keith", "christian", "roger", "noah", "gerald", "carl", "terry",
    "sean", "austin", "arthur", "lawrence", "jesse", "dylan", "bryan", "joe",
    "jordan", "billy", "bruce", "albert", "willie", "gabriel", "logan", "alan",
    "juan", "wayne", "roy", "ralph", "randy", "eugene", "vincent", "russell",
    "louis", "philip", "bobby", "johnny", "bradley", "joel", "luke", "martin",
    "mary", "patricia", "jennifer", "linda", "elizabeth", "barbara", "susan",
    "jessica", "sarah", "karen", "nancy", "lisa", "margaret", "betty", "sandra",
    "ashley", "dorothy", "kimberly", "emily", "donna", "michelle", "carol",
    "amanda", "melissa", "deborah", "stephanie", "rebecca", "sharon", "laura",
    "cynthia", "kathleen", "amy", "shirley", "angela", "helen", "anna", "brenda",
    "pamela", "nicole", "samantha", "katherine", "emma", "ruth", "christine",
    "catherine", "debra", "rachel", "carolyn", "janet", "virginia", "maria",
    "heather", "diane", "julie", "joyce", "victoria", "kelly", "christina",
    "lauren", "joan", "evelyn", "olivia", "judith", "megan", "cheryl", "martha",
    "andrea", "frances", "hannah", "jacqueline", "ann", "gloria", "jean", "kathryn",
    "alice", "teresa", "sara", "janice", "doris", "madison", "julia", "grace",
    "judy", "abigail", "marie", "denise", "beverly", "amber", "theresa", "marilyn",
    "danielle", "diana", "brittany", "natalie", "sophia", "rose", "isabella",
    "alexis", "kayla", "charlotte",

    # Top Global Surnames
    "smith", "johnson", "williams", "brown", "jones", "garcia", "miller", "davis",
    "rodriguez", "martinez", "hernandez", "lopez", "gonzalez", "wilson", "anderson",
    "thomas", "taylor", "moore", "jackson", "martin", "lee", "perez", "thompson",
    "white", "harris", "sanchez", "clark", "ramirez", "lewis", "robinson", "walker",
    "young", "allen", "king", "wright", "scott", "torres", "nguyen", "hill",
    "flores", "green", "adams", "nelson", "baker", "hall", "rivera", "campbell",
    "mitchell", "carter", "roberts", "gomez", "phillips", "evans", "turner",
    "diaz", "parker", "cruz", "edwards", "collins", "reyes", "stewart", "morris",
    "morales", "murphy", "cook", "rogers", "gutierrez", "ortiz", "morgan", "cooper",
    "peterson", "bailey", "reed", "kelly", "howard", "ramos", "kim", "cox", "ward",
    "richardson", "watson", "brooks", "chavez", "wood", "james", "bennett", "gray",
    "mendoza", "ruiz", "hughes", "price", "alvarez", "castillo", "sanders", "patel",
    "myers", "long", "ross", "foster", "jimenez", "powell", "jenkins", "perry",
    "russell", "sullivan", "bell", "coleman", "butler", "henderson", "barnes",
    "gonzales", "fisher", "vasquez", "simmons", "romero", "jordan", "patterson",
    "alexander", "hamilton", "graham", "reynolds", "griffin", "wallace", "moreno",
    "west", "cole", "hayes", "bryant", "herrera", "gibson", "ellis", "tran",
    "medina", "aguilar", "stevens", "murray", "ford", "castro", "marshall", "owens",
    "harrison", "fernandez", "mcdonald", "woods", "washington", "kennedy", "wells",
    "vargas", "henry", "chen", "freeman", "webb", "tucker", "guzman", "burns",
    "crawford", "olson", "simpson", "porter", "hunter", "gordon", "mendez", "silva",
    "shaw", "snyder", "mason", "dixon", "munoz", "hunt", "hicks", "holmes",
    "palmer", "wagner", "black", "robertson", "boyd", "rose", "stone", "salazar",
    "fox", "warren", "mills", "meyer", "rice", "schmidt", "garza", "daniels",
    "ferguson", "nichols", "stephens", "soto", "weaver", "ryan", "gardner",
    "payne", "grant", "dunn", "kelley", "spencer", "hawkins", "arnold", "pierce",
]

INDIAN_SURNAMES = [
    # North, West, Central Indian Surnames
    "sharma", "verma", "gupta", "singh", "kumar", "yadav", "mishra", "tiwari",
    "pandey", "patel", "shah", "mehta", "joshi", "desai", "kulkarni", "deshmukh",
    "patil", "shinde", "pawar", "chauhan", "rajput", "bhardwaj", "shukla", "tripathi",
    "dwivedi", "upadhyay", "dubey", "chaubey", "saxena", "bhatnagar", "mathur",
    "srivastava", "agarwal", "agrawal", "mittal", "bansal", "garg", "goel", "goyal",
    "jindal", "singhal", "kansal", "maheshwari", "khandelwal", "somani", "birla",
    "bajaj", "ambani", "adani", "tata", "godrej", "mittal", "modi", "choksi",
    "parikh", "shah", "sheth", "vora", "zala", "solanki", "chudasama", "gohil",
    "jadeja", "jhala", "rathore", "sisodia", "shekhawat", "parmar", "tomar",
    "tanwar", "bhati", "gehlot", "scindia", "holkar", "gaekwad", "bhonsle",
    "kadamb", "more", "jadhav", "bhosale", "chavan", "salunkhe", "sawant", "mane",
    "thorat", "ghatge", "mohite", "shirke", "mahale", "gaikwad", "kamble", "lokhande",
    "wankhede", "meshram", "khobragade", "gavai", "borkar", "tambde", "pednekar",
    "karandikar", "tendulkar", "gavaskar", "kirmani", "vengsarkar", "manjrekar",
    "dandekar", "kelkar", "gore", "phadke", "tilak", "gokhale", "ranade", "chiplunkar",
    "agarkar", "karve", "savarkar", "godse", "apte", "kanitkar", "deodhar", "purandare",
    "bapat", "modak", "limaye", "pendse", "paranjape", "godbole", "sathe", "lele",

    # South Indian Surnames & Family Names (Tamil, Telugu, Kannada, Malayalam)
    "rao", "reddy", "naidu", "nair", "menon", "pillai", "nambiar", "iyer",
    "iyengar", "balasubramanian", "raman", "krishnan", "murthy", "bhat", "hegde",
    "shetty", "gowda", "kamath", "pai", "kudva", "shenoy", "mallya", "prabhu",
    "nayak", "kinni", "acharya", "somayaji", "upadhyaya", "tantri", "adiga",
    "herle", "ballal", "alva", "chowta", "punja", "marla", "rai", "bhandary",
    "kulal", "moolya", "kotian", "poojary", "suvarna", "amin", "salian", "karkera",
    "anjaneyulu", "subbarao", "venkatesh", "venkataraman", "swaminathan", "subramanian",
    "narayanan", "viswanathan", "srinivasan", "raghavan", "soundararajan", "kannan",
    "anand", "vijayan", "chandran", "gopal", "govind", "madhavan", "kesavan",
    "radhakrishnan", "ramachandran", "sundaram", "natarajan", "sankaran", "mahadevan",
    "parthasarathy", "soundararajan", "seshadri", "kasturirangan", "varadarajan",
    "chari", "chetty", "chetti", "gounder", "vanniyar", "thevar", "nadar",
    "mudaliar", "chettiar", "valluvar", "maravar", "kallar", "agamudayar",
    "kurup", "variar", "warrier", "pisharody", "marar", "poduval", "panicker",
    "unni", "kartha", "kaimal", "thampi", "thirumulpad", "koilthampuran",
    "namboodiri", "bhattathiri", "embrandiri", "elayath", "moosad", "moothathu",

    # East & Northeast Indian Surnames (Bengali, Odia, Assamese)
    "das", "banerjee", "mukherjee", "chatterjee", "bose", "ghosh", "dutta", "sen",
    "roy", "chakraborty", "majumdar", "barua", "saikia", "gogoi", "bhattacharya",
    "ganguly", "goswami", "lahiri", "sanyal", "bagchi", "moitra", "bhaduri",
    "choudhury", "chowdhury", "biswas", "sarkar", "kundu", "pal", "de", "dhar",
    "kar", "nandi", "mitra", "guha", "basu", "som", "nag", "rudra", "chanda",
    "bardhan", "mallick", "adhikari", "patra", "pradhan", "mohanty", "patnaik",
    "behera", "mishra", "rath", "satapathy", "tripathy", "mohapatra", "dash",
    "samal", "jena", "nayak", "rout", "sahoo", "swain", "lenka", "barik",
    "bora", "phukan", "kakati", "hazarika", "medhi", "kalita", "deka", "tamuly",
    "mahanta", "sarma", "barman", "chaliha", "bezbaruah",

    # Indian Christian Surnames (Goan, Mangalorean, Anglo-Indian, Kerala)
    "fernandes", "dsouza", "d'souza", "lobo", "pinto", "pereira", "rodrigues",
    "anthony", "thomas", "joseph", "mathew", "varghese", "kurian", "chacko",
    "george", "philip", "koshy", "abraham", "cherian", "oommen", "jacob",
    "john", "samuel", "stephen", "alexander", "paul", "peter", "simon",
    "coelho", "sequeira", "souza", "albuquerque", "braganza", "cardoso", "carvalho",
    "coutinho", "cruz", "cunha", "faria", "figueiredo", "fonseca", "gonsalves",
    "machado", "mascarenhas", "menezes", "miranda", "noronha", "quadros", "rebello",
    "rego", "saldanha", "tavares", "vaz", "viegas",

    # Indian Muslim Surnames
    "ali", "khan", "ahmed", "siddiqui", "qureshi", "ansari", "shaikh", "sheikh",
    "syed", "sayed", "mirza", "baig", "beg", "patel", "momin", "mansoori",
    "choudhary", "malik", "hassan", "husain", "hussain", "farooqui", "usmani",
    "abbasi", "hashmi", "alvi", "rizvi", "zaidi", "kazmi", "naqvi", "jafri",
]

HINGLISH_VOCABULARY = [
    # Pronouns & determiners
    "aap", "tum", "tu", "hum", "main", "mera", "meri", "mere", "tera", "teri", "tere",
    "unka", "unki", "unke", "uska", "uski", "uske", "inka", "inki", "inke", "iska", "iski", "iske",
    "kiska", "kiski", "kiske", "apna", "apni", "apne", "humara", "humari", "humare", "tumhara",
    "tumhari", "tumhare",

    # Interrogatives
    "kya", "kyun", "kaise", "kab", "kahan", "kidhar", "kitna", "kitni", "kitne", "kaun",
    "kisko", "kisse", "kinhe",

    # Common verbs & auxiliaries
    "hai", "hain", "ho", "hoon", "hun", "tha", "thi", "the", "hoga", "hogi", "hoge", "honge",
    "kar", "karo", "karna", "karta", "karti", "karte", "karunga", "karungi", "karenge", "kiya", "kiye", "ki",
    "hona", "hota", "hoti", "hote", "hua", "hui", "hue",
    "de", "do", "dena", "deta", "deti", "dete", "dunga", "dungi", "denge", "diya", "diye",
    "le", "lo", "lena", "leta", "leti", "lete", "lunga", "lungi", "lenge", "liya", "liye",
    "bol", "bolo", "bolna", "bolta", "bolti", "bolte", "bata", "batao", "batana", "batata", "batati", "batate",
    "dekh", "dekho", "dekhna", "dekhta", "dekhti", "dekhte", "dekha", "dekhi", "dekhe",
    "sun", "suno", "sunna", "sunta", "sunti", "sunte", "suna", "suni", "sune",
    "ja", "jao", "jana", "jata", "jati", "jate", "jaunga", "jaungi", "jayenge", "gaya", "gayi", "gaye",
    "aa", "aao", "aana", "aata", "aati", "aate", "aaunga", "aaungi", "aayenge", "aaya", "aayi", "aaye",
    "raha", "rahi", "rahe", "rakh", "rakho", "rakhna", "rakha", "rakhi", "rakhe",
    "lag", "laga", "lagi", "lage", "lagta", "lagti", "lagte", "lagna",
    "samajh", "samjha", "samjhi", "samjhe", "samajhna", "samajhta", "samajhti",

    # Adverbs, particles, adjectives & everyday conversational words
    "haan", "nahi", "nahin", "na", "mat", "bhi", "hi", "to", "toh", "tak", "se", "mein", "par",
    "ko", "ne", "aur", "ya", "lekin", "magar", "parantu", "kyunki", "isliye",
    "accha", "achha", "achhi", "achhe", "theek", "sahi", "galat", "badiya", "mast", "zabardast",
    "bahut", "bohot", "thoda", "thodi", "thode", "zyada", "jyada", "kam", "pura", "puri", "pure",
    "aaj", "kal", "parson", "subah", "dopahar", "shaam", "raat", "abhi", "pehle", "baad", "hamesha",
    "kabhi", "jaldi", "dheere", "shuru", "khatam", "paas", "door", "upar", "neeche", "aage", "peeche",
    "andar", "bahar", "saath", "alag", "sirf", "bas", "khud", "khushi", "pyaar", "dost", "dosti",
    "bhai", "bhaiya", "bro", "didi", "yaar", "boss", "dude", "chalta", "pakka", "jugaad", "jhakaas",
    "bindaas", "fundoo", "bhasad", "vela", "nalla", "dhansu", "locha", "setting", "scene", "pataka",
]

INDIAN_FIRST_NAMES = [
    # Male Given Names
    "rahul", "rohit", "amit", "rohan", "vikram", "aditya", "arjun", "rajesh",
    "suresh", "ramesh", "mahesh", "dinesh", "ganesh", "harish", "naresh",
    "mukesh", "ashok", "manoj", "sanjay", "ajay", "vijay", "prakash", "deepak",
    "sunil", "anil", "sachin", "sourav", "virat", "chetan", "ashwin", "ravindra",
    "hardik", "jasprit", "rishabh", "shreyas", "shubman", "sanju", "samson",
    "abhishek", "aakash", "akash", "akshay", "aman", "aniket", "ankit", "anurag",
    "aravind", "arvind", "ashish", "avinash", "ayush", "bharat", "bhavesh",
    "chirag", "darshan", "dev", "dhruv", "gaurav", "girish", "harsh", "hemant",
    "hitesh", "ishaan", "ishan", "jagdish", "jitendra", "karan", "kartik",
    "karthik", "kaushik", "kishore", "krishna", "kunal", "lalit", "madhav",
    "manish", "mayank", "mohit", "mukund", "nakul", "naveen", "navin", "neeraj",
    "nidhi", "nikhil", "nilesh", "nitin", "pankaj", "parth", "pranav", "pranay",
    "prasad", "prashant", "prateek", "praveen", "prem", "prithvi", "raghav",
    "raj", "rajan", "rajeev", "rajiv", "rakesh", "ram", "rishi", "ritesh",
    "ronit", "sameer", "samir", "sandeep", "sandip", "sarvesh", "saurabh",
    "shantanu", "shashi", "shivam", "shiv", "siddharth", "sid", "soham",
    "somesh", "sparsh", "srikar", "subhash", "sudhir", "suhas", "suraj",
    "swapnil", "tanmay", "tarun", "tejas", "tushar", "uday", "ujjwal", "umang",
    "umesh", "utkarsh", "vaibhav", "varun", "vasant", "ved", "vicky", "vignesh",
    "vihan", "vijendra", "vimal", "vinay", "vineet", "vinod", "vipul", "vishal",
    "vishnu", "vivek", "yash", "yogesh",

    # Female Given Names
    "priya", "pooja", "puja", "ananya", "sneha", "neha", "riya", "shreya",
    "divya", "kavya", "anjali", "meera", "deepa", "lakshmi", "swati", "sunita",
    "geeta", "gita", "aditi", "aishwarya", "akanksha", "alisha", "amrita",
    "anamika", "anita", "ankita", "anushka", "aparna", "archana", "arti", "aarti",
    "aruna", "asha", "avni", "bhavana", "bhavna", "chaya", "chetna", "deepika",
    "deepti", "disha", "dolly", "drasti", "garima", "gayatri", "harini", "hema",
    "ishita", "janaki", "jaya", "jyoti", "kamala", "kanika", "karishma", "kiran",
    "komal", "kruti", "madhu", "madhuri", "malini", "manisha", "mansi", "maya",
    "mithila", "mona", "monika", "nandini", "natasha", "nidhi", "nikita",
    "nisha", "pallavi", "payal", "prachi", "pragya", "pratibha", "preeti",
    "prerna", "rachana", "radha", "radhika", "ragini", "rajni", "rakhi",
    "rashmi", "rekha", "renuka", "richa", "rinku", "ritika", "ritu", "roshni",
    "rupa", "sakshi", "samiksha", "sangeeta", "sangita", "santhi", "sapna",
    "sarita", "saroj", "seema", "shalini", "shanta", "sheela", "shikha",
    "shilpa", "shivani", "shobha", "shraddha", "shruthi", "shruti", "simran",
    "smita", "sonal", "sonali", "sonia", "soumya", "sowmya", "sruthi", "srishti",
    "subhadra", "suchitra", "sudha", "sujata", "sukanya", "sulochana", "suman",
    "sumitra", "sunaina", "supriya", "surabhi", "sushma", "sushmita", "sweta",
    "tanvi", "tanu", "tanya", "tara", "trisha", "tulsi", "uma", "upasana",
    "urvashi", "usha", "vaishali", "vandana", "vani", "varsha", "vidya",
    "vijaya", "vrinda", "yamini", "yashasvi",
]

INDIAN_GEOGRAPHY = [
    # States and Union Territories
    "india", "kerala", "karnataka", "tamilnadu", "tamil nadu", "andhra", "andhra pradesh",
    "telangana", "maharashtra", "gujarat", "rajasthan", "punjab", "haryana",
    "uttar pradesh", "bihar", "bengal", "west bengal", "odisha", "assam", "goa",
    "madhya pradesh", "chhattisgarh", "jharkhand", "himachal", "himachal pradesh",
    "uttarakhand", "jammu", "kashmir", "ladakh", "sikkim", "meghalaya", "manipur",
    "mizoram", "nagaland", "tripura", "arunachal", "arunachal pradesh", "delhi",
    "chandigarh", "puducherry", "pondicherry", "andaman", "nicobar", "lakshadweep",

    # Major Cities & Towns
    "mumbai", "bombay", "delhi", "bengaluru", "bangalore", "hyderabad", "chennai",
    "madras", "kolkata", "calcutta", "pune", "ahmedabad", "surat", "jaipur",
    "lucknow", "kanpur", "nagpur", "indore", "thane", "bhopal", "visakhapatnam",
    "vizag", "patna", "vadodara", "baroda", "ghaziabad", "ludhiana", "agra",
    "nashik", "faridabad", "meerut", "rajkot", "varanasi", "banaras", "kashi",
    "srinagar", "aurangabad", "chhatrapati sambhajinagar", "dhanbad", "amritsar",
    "navi mumbai", "allahabad", "prayagraj", "ranchi", "howrah", "coimbatore",
    "jabalpur", "gwalior", "vijayawada", "jodhpur", "madurai", "raipur", "kota",
    "guwahati", "solapur", "hubli", "dharwad", "bareilly", "moradabad", "mysore",
    "mysuru", "tiruppur", "gurgaon", "gurugram", "aligarh", "jalandhar", "bhubaneswar",
    "salem", "warangal", "guntur", "bhiwandi", "saharanpur", "gorakhpur", "bikaner",
    "amravati", "noida", "jamshedpur", "bhilai", "cuttack", "firozabad", "kochi",
    "cochin", "nellore", "bhavnagar", "dehradun", "durgapur", "asansol", "rourkela",
    "nanded", "kolhapur", "ajmer", "akola", "gulbarga", "kalaburagi", "jamnagar",
    "ujjain", "loni", "siliguri", "jhansi", "ulhasnagar", "sangli", "mangalore",
    "mangaluru", "erode", "belgaum", "belagavi", "ambattur", "tirunelveli", "malegaon",
    "gaya", "jalgaon", "udaipur", "maheshtala", "kozhikode", "calicut",
    "thiruvananthapuram", "trivandrum", "thrissur", "kollam", "alappuzha", "alleppey",
    "kottayam", "palakkad", "kannur", "kasaragod", "wayanad", "idukki", "pathanamthitta",
    "ernakulam", "panaji", "margao", "vasco", "mapusa", "shimla", "manali", "dharamshala",
    "rishikesh", "haridwar", "mussoorie", "nainital", "rohtak", "panipat", "karnal",
    "kurukshetra", "ambala", "bathinda", "pathankot", "patiala", "mohali", "panchkula",
]

INDIAN_FOOD_AND_CULTURE = [
    # Dishes & Meals
    "biryani", "biriyani", "dosa", "masala dosa", "samosa", "roti", "naan", "paneer",
    "tikka", "butter chicken", "dal", "daal", "tadka", "khichdi", "idli", "vada",
    "sambar", "rasam", "upma", "poha", "paratha", "puri", "poori", "chole",
    "bhature", "pav", "bhaji", "pav bhaji", "vada pav", "misal", "chaat",
    "pani puri", "golgappa", "sev", "bhujia", "lassi", "chai", "halwa",
    "gulab jamun", "rasgulla", "jalebi", "kheer", "barfi", "laddoo", "laddu",
    "kaju katli", "chutney", "achaar", "achar", "papad", "raita", "ghee",
    "pakora", "pakoda", "bhindi", "aloo", "gobi", "matar", "palak", "paneer butter masala",
    "dal makhani", "korma", "nihari", "haleem", "sheermal", "kulcha", "kachori",
    "dhokla", "khandvi", "thepla", "fafda", "undhiyu", "shrikhand", "basundi",
    "sandesh", "mishti doi", "cham cham", "rasmalai", "malpua", "ghevar",
    "appam", "puttu", "payasam", "porotta", "avial", "thoran", "olan", "kaalan",
    "mor kuzhambu", "vatha kuzhambu", "kootu", "pongal", "bisi bele bath",
    "kesari bath", "mysore pak", "dharwad peda", "obbattu", "holige", "akki roti",
    "ragi mudde", "pattichu", "pachadi", "pulihora", "pesarattu", "gongura",

    # Spices & Ingredients
    "masala", "garam masala", "methi", "pudina", "dhaniya", "jeera", "haldi",
    "hing", "elaichi", "laung", "dalchini", "saunf", "ajwain", "rai", "sarson",
    "amchur", "kasuri methi", "tejpata", "kokum", "tamarind", "jaggery", "gur",
    "atta", "maida", "besan", "sooji", "rava", "chana", "urad", "moong", "toor",
    "rajma", "kabuli chana", "kala chana", "masoor", "makhana", "pista", "badam",
    "kaju", "kishmish", "kesar",

    # Cultural & Common Hinglish / Slang Expressions
    "namaste", "namaskar", "salaam", "vanakkam", "dhanyavad", "shukriya",
    "bhai", "yaar", "pakka", "jugaad", "desi", "accha", "acha", "theek",
    "bas", "arre", "arey", "boss", "anna", "chetta", "bhaiyya", "bhau", "didi",
    "chalo", "dekho", "suno", "kya", "kyun", "kaise", "kahan", "kab", "kitna",
    "bolo", "batao", "samajh", "pata", "maal", "funda", "bindaas", "mast",
    "jhakaas", "chalta hai", "timepass", "scene", "shanti", "pucca", "khiladi",
    "crore", "lakh", "diwali", "deepavali", "holi", "eid", "onam", "pongal",
    "navratri", "dussehra", "durga puja", "ganesh chaturthi", "rakhi", "baisakhi",
    "bihu", "karwa chauth", "chath puja", "makar sankranti", "gurpurab",
]


def load_corpus_frequencies(corpus_path):
    """Reads word frequencies from count_1w.txt into a lowercase dictionary."""
    print(f"Loading corpus from {corpus_path}...")
    corpus = {}
    if not os.path.exists(corpus_path):
        print(f"Warning: Corpus {corpus_path} not found. Default weights will be used.")
        return corpus

    with open(corpus_path, "r", encoding="utf-8") as f:
        for line in f:
            parts = line.strip().split()
            if len(parts) >= 2:
                try:
                    w = parts[0].lower()
                    freq = int(parts[1])
                    corpus[w] = freq
                except ValueError:
                    continue
    print(f"Loaded {len(corpus)} corpus frequencies.")
    return corpus


def normalize_freq(freq, max_freq):
    """Log-scales raw frequencies into 1-255."""
    return max(1, min(255, int((math.log(freq + 1) / math.log(max_freq + 1)) * 255)))


def build_trie(rows):
    """Builds and BFS-flattens an FLCT prefix trie."""
    class Node:
        def __init__(self):
            self.children = {}
            self.freq = 0
            self.is_terminal = False
            self.offset = -1
            self.char = ""

    root = Node()
    max_freq = max((freq for _, freq in rows), default=0)
    print(f"Max frequency found: {max_freq}")

    for word, freq in rows:
        norm_freq = normalize_freq(freq, max_freq)

        current = root
        for char in word:
            if char not in current.children:
                current.children[char] = Node()
                current.children[char].char = char
            current = current.children[char]

        current.is_terminal = True
        if norm_freq > current.freq:
            current.freq = norm_freq

    print("Flattening trie with BFS...")
    nodes = []
    queue = [root]
    while queue:
        node = queue.pop(0)
        nodes.append(node)
        for char, child in sorted(node.children.items()):
            queue.append(child)

    current_offset = 4  # 4 bytes for magic header 'FLCT'
    for node in nodes:
        node.offset = current_offset
        child_count = min(255, len(node.children))
        node_size = 3 + (child_count * 6)
        current_offset += node_size

    print(f"Total nodes: {len(nodes)}")
    print(f"Expected file size: {current_offset} bytes ({current_offset / 1024:.1f} KB)")
    return nodes


def write_flictionary(nodes, output_file):
    """Writes nodes to binary FLCT file."""
    print(f"Writing binary FLCT trie to {output_file}...")
    os.makedirs(os.path.dirname(os.path.abspath(output_file)), exist_ok=True)
    with open(output_file, "wb") as f:
        f.write(b"FLCT")
        for node in nodes:
            children_items = sorted(node.children.items())[:255]
            child_count = len(children_items)

            # Node header: freq (1B), is_terminal (1B), child_count (1B)
            f.write(struct.pack(">BBB", node.freq, 1 if node.is_terminal else 0, child_count))

            # Children: char (2B utf-16be), child_offset (4B int)
            for char, child in children_items:
                char_bytes = char.encode("utf-16-be")
                if len(char_bytes) > 2:
                    char_bytes = char_bytes[:2]
                elif len(char_bytes) < 2:
                    char_bytes = char_bytes.ljust(2, b"\x00")

                f.write(char_bytes)
                f.write(struct.pack(">I", child.offset))

    print(f"Successfully generated {output_file} ({os.path.getsize(output_file)} bytes).")


TYPO_BLOCKLIST = {
    "alot", "teh", "hte", "adn", "thier", "untill", "definately", "seperate", "occured",
    "dont", "cant", "wont", "isnt", "arent", "hasnt", "havent", "didnt", "couldnt", "shouldnt", "wouldnt",
}


def load_external_tokens(vocab_dir=r"E:\gtmp\fluxboard-vocab"):
    """Loads Wikidata names (CC0) and GeoNames populated places (CC BY 4.0) if available."""
    tokens = set()
    if not os.path.exists(vocab_dir):
        return tokens

    # 1. Wikidata Given Names & Surnames (CC0)
    for fn in ["wikidata_given_tokens.txt", "wikidata_surname_tokens.txt"]:
        p = os.path.join(vocab_dir, fn)
        if os.path.exists(p):
            with open(p, "r", encoding="utf-8") as f:
                for line in f:
                    line = line.strip().lower()
                    if (
                        line
                        and not line.startswith("#")
                        and line.isalpha()
                        and 2 <= len(line) <= 24
                        and line not in TYPO_BLOCKLIST
                    ):
                        tokens.add(line)

    # 2. GeoNames India Populated Places (CC BY 4.0, filtered pop >= 10,000)
    in_zip = os.path.join(vocab_dir, "IN.zip")
    if os.path.exists(in_zip):
        import zipfile
        import re
        with zipfile.ZipFile(in_zip) as archive:
            with archive.open("IN.txt") as source:
                for raw in source:
                    fields = raw.decode("utf-8").rstrip("\n").split("\t")
                    if len(fields) < 15 or fields[6] != "P":
                        continue
                    try:
                        pop = int(fields[14] or "0")
                    except ValueError:
                        pop = 0
                    if pop >= 10000:
                        labels = [fields[1], fields[2]] + fields[3].split(",")
                        for label in labels:
                            for part in re.split(r"[\s\'’ʼ-]+", label.lower()):
                                clean = "".join(c for c in part if c.isalpha() and c.isascii())
                                if 3 <= len(clean) <= 24 and clean not in TYPO_BLOCKLIST:
                                    tokens.add(clean)

    print(f"Loaded {len(tokens)} external tokens from {vocab_dir}")
    return tokens


def main():
    corpus_file = os.path.join(os.path.dirname(__file__), "count_1w.txt")
    output_file = os.path.join(
        os.path.dirname(__file__),
        "..",
        "keyboard-core",
        "src",
        "main",
        "assets",
        "names_dict.bin",
    )

    corpus = load_corpus_frequencies(corpus_file)
    max_corpus_freq = max(corpus.values(), default=10_000_000_000)

    # Collect all words
    all_word_lists = [
        GLOBAL_NAMES,
        INDIAN_SURNAMES,
        INDIAN_FIRST_NAMES,
        INDIAN_GEOGRAPHY,
        INDIAN_FOOD_AND_CULTURE,
        HINGLISH_VOCABULARY,
    ]

    unique_words = set()
    for wlist in all_word_lists:
        for item in wlist:
            clean = item.strip().lower()
            # Split multi-word tokens like "butter chicken" into individual words,
            # as well as keeping the combined or hyphenated form if single word
            if " " in clean:
                for sub in clean.split():
                    if sub.isalpha() and len(sub) > 1:
                        unique_words.add(sub)
            else:
                if clean.isalpha() and len(clean) > 1:
                    unique_words.add(clean)

    print(f"Total curated unique words: {len(unique_words)}")
    external_tokens = load_external_tokens()
    unique_words.update(external_tokens)
    print(f"Total combined unique words with external sources: {len(unique_words)}")

    # Assign frequencies
    # If present in count_1w.txt, take the real web count.
    # If not present in count_1w.txt, assign a solid base count (e.g. 100,000) so it log-scales to ~180-200.
    rows = []
    DEFAULT_UNLISTED_FREQ = 250_000  # Will yield strong ~180 log frequency

    for word in sorted(unique_words):
        freq = corpus.get(word, DEFAULT_UNLISTED_FREQ)
        # Ensure minimum baseline frequency so valid names are never drowned out
        freq = max(freq, DEFAULT_UNLISTED_FREQ)
        rows.append((word, freq))

    print(f"Assembled {len(rows)} entries for names_dict.bin")
    nodes = build_trie(rows)
    write_flictionary(nodes, output_file)


if __name__ == "__main__":
    main()
