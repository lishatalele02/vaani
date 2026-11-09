"""SYNTHETIC training phrases written by the project author.

IMPORTANT: this is a small hand-written dataset used only to bootstrap the
intent classifier. It is NOT real patient or clinical data and does not
represent real impaired speech. Each entry: intent -> English, Hindi
(Devanagari + Roman), Marathi (Devanagari + Roman) example utterances.
"""
from __future__ import annotations

PHRASES = {
    "request_water": {
        "en": ["I need water", "Please give me water", "I am thirsty", "Can I have some water", "Bring me a glass of water", "I want to drink water", "Water please"],
        "hi": ["मुझे पानी चाहिए", "पानी दे दो", "मुझे प्यास लगी है", "थोड़ा पानी लाओ", "mujhe paani chahiye", "paani do", "pyaas lagi hai", "thoda pani dena"],
        "mr": ["मला पाणी पाहिजे", "पाणी द्या", "मला तहान लागली आहे", "थोडं पाणी आणा", "mala pani pahije", "pani dya", "tahan lagli ahe", "mala pani de"],
    },
    "request_food": {
        "en": ["I am hungry", "I need food", "Please give me something to eat", "I want to eat", "Bring me my meal", "Can I have food", "Food please"],
        "hi": ["मुझे भूख लगी है", "खाना दो", "मुझे खाना चाहिए", "कुछ खाने को दो", "mujhe bhookh lagi hai", "khana do", "mujhe khana chahiye", "kuch khane ko do"],
        "mr": ["मला भूक लागली आहे", "मला जेवण पाहिजे", "जेवायला द्या", "काहीतरी खायला द्या", "mala bhook lagli ahe", "mala jevan pahije", "jevayla dya", "khayla dya"],
    },
    "request_help": {
        "en": ["Please help me", "I need help", "Help me", "Can someone help me", "I need assistance", "Somebody help", "Please come here and help"],
        "hi": ["मेरी मदद करो", "मुझे मदद चाहिए", "कोई मदद करो", "मुझे सहायता चाहिए", "meri madad karo", "mujhe madad chahiye", "koi help karo", "help chahiye"],
        "mr": ["मला मदत हवी", "माझी मदत करा", "कोणीतरी मदत करा", "मला सहाय्य पाहिजे", "mala madat havi", "majhi madat kara", "konitari madat kara", "mala help pahije"],
    },
    "request_bathroom": {
        "en": ["I need to use the bathroom", "Take me to the toilet", "I need the washroom", "I have to go to the toilet", "Bathroom please", "I want to go to the restroom", "Help me to the toilet"],
        "hi": ["मुझे बाथरूम जाना है", "मुझे शौचालय जाना है", "टॉयलेट ले चलो", "मुझे वॉशरूम ले चलो", "mujhe bathroom jana hai", "toilet jana hai", "mujhe washroom le chalo", "bathroom jaana hai"],
        "mr": ["मला बाथरूमला जायचे आहे", "मला संडासला जायचंय", "टॉयलेटला घेऊन चला", "मला स्वच्छतागृहात जायचे आहे", "mala bathroom la jayche ahe", "mala toilet la jayche ahe", "mala sandas la jayche", "washroom la ghevun chala"],
    },
    "express_pain": {
        "en": ["I am in pain", "It hurts", "I have pain", "My body is paining", "It hurts a lot here", "I feel severe pain", "My head hurts"],
        "hi": ["मुझे दर्द हो रहा है", "बहुत दर्द है", "यहाँ दर्द हो रहा है", "मेरा सिर दर्द कर रहा है", "mujhe dard ho raha hai", "bahut dard hai", "yaha dard ho raha hai", "sar dard kar raha hai"],
        "mr": ["मला दुखत आहे", "खूप वेदना होत आहेत", "इथे दुखतंय", "माझं डोकं दुखतंय", "mala dukhat ahe", "khup vedna hot aahet", "ithe dukhtay", "majhe doke dukhtay"],
    },
    "request_medicine": {
        "en": ["I need my medicine", "Give me my tablet", "It is time for my medicine", "Please bring my pills", "I want to take my medicine", "Where is my medicine", "Medicine please"],
        "hi": ["मुझे मेरी दवा चाहिए", "दवा दो", "मेरी गोली दो", "दवा का समय हो गया", "mujhe meri dawai chahiye", "dawa do", "meri goli do", "dawai ka time ho gaya"],
        "mr": ["मला माझे औषध हवे", "औषध द्या", "माझी गोळी द्या", "औषधाची वेळ झाली", "mala maze aushadh have", "aushadh dya", "majhi goli dya", "aushadhachi vel zali"],
    },
    "request_rest": {
        "en": ["I need to rest", "I want to lie down", "I am tired", "Let me rest for a while", "I feel exhausted", "I need some rest", "I want to sit down"],
        "hi": ["मुझे आराम करना है", "मैं थक गया हूँ", "मुझे लेटना है", "थोड़ा आराम करने दो", "mujhe aaram karna hai", "main thak gaya hoon", "mujhe letna hai", "thoda aaram karne do"],
        "mr": ["मला विश्रांती घ्यायची आहे", "मी थकलो आहे", "मला पडायचे आहे", "थोडा आराम करू दे", "mala vishranti ghyaychi ahe", "mi thakalo ahe", "mala aaram karaycha ahe", "thoda aaram karu de"],
    },
    "request_sleep": {
        "en": ["I want to sleep", "I am sleepy", "I need to go to bed", "It is time for me to sleep", "Turn off the lights I want to sleep", "I feel sleepy", "Good night I am going to sleep"],
        "hi": ["मुझे सोना है", "मुझे नींद आ रही है", "मुझे सोने जाना है", "लाइट बंद करो मुझे सोना है", "mujhe sona hai", "mujhe neend aa rahi hai", "mujhe sone jana hai", "light band karo mujhe sona hai"],
        "mr": ["मला झोपायचे आहे", "मला झोप येत आहे", "मला झोपायला जायचे आहे", "लाईट बंद करा मला झोपायचे आहे", "mala zopaychay", "mala zop yet ahe", "mala zopayla jayche ahe", "light band kara mala zopaychay"],
    },
    "express_discomfort": {
        "en": ["I feel uncomfortable", "I am not comfortable", "This is uncomfortable", "I feel restless", "Something feels wrong", "I am not feeling well", "I feel uneasy"],
        "hi": ["मुझे बेचैनी हो रही है", "मुझे ठीक नहीं लग रहा", "मुझे असहज लग रहा है", "यह आरामदायक नहीं है", "mujhe bechaini ho rahi hai", "mujhe theek nahi lag raha", "mujhe asahaj lag raha hai", "yeh aaramdayak nahi hai"],
        "mr": ["मला अस्वस्थ वाटत आहे", "मला बरे वाटत नाही", "मला अवघडल्यासारखे वाटते", "हे सोयीचे नाही", "mala asvasth vatat ahe", "mala bare vatat nahi", "mala avghadlyasarkhe vatte", "he soyiche nahi"],
    },
    "request_family": {
        "en": ["Please call my family", "I want to see my family", "Call my son", "I want to talk to my daughter", "Please contact my relatives", "Bring my family here", "I want my family"],
        "hi": ["मेरे परिवार को बुलाओ", "मुझे अपने घरवालों से बात करनी है", "मेरे बेटे को फोन करो", "परिवार को बुलाओ", "mere parivar ko bulao", "mujhe ghar walon se baat karni hai", "mere bete ko phone karo", "family ko call karo"],
        "mr": ["माझ्या कुटुंबाला बोलवा", "मला घरच्यांशी बोलायचे आहे", "माझ्या मुलाला फोन करा", "कुटुंबाला कळवा", "majhya kutumbala bolva", "mala gharchyanshi bolaycha ahe", "majhya mulala phone kara", "family la call kara"],
    },
    "request_emergency_help": {
        "en": ["This is an emergency", "I need urgent help", "Call an ambulance", "Emergency please come quickly", "I need help right now it is serious", "Please call 108 now", "Save me quickly"],
        "hi": ["यह इमरजेंसी है", "मुझे तुरंत मदद चाहिए", "एम्बुलेंस बुलाओ", "जल्दी आओ बहुत जरूरी है", "yeh emergency hai", "mujhe turant madad chahiye", "ambulance bulao", "jaldi aao bahut zaroori hai"],
        "mr": ["ही आणीबाणी आहे", "मला तातडीने मदत हवी", "रुग्णवाहिका बोलवा", "लवकर या खूप गरजेचे आहे", "hi anibani ahe", "mala tatadine madat havi", "ambulance bolva", "lavkar ya khup garajeche ahe"],
    },
    "request_doctor": {
        "en": ["I need a doctor", "Please call the doctor", "I want to see a doctor", "Take me to the hospital", "I need medical attention", "Bring the doctor", "Doctor please"],
        "hi": ["मुझे डॉक्टर चाहिए", "डॉक्टर को बुलाओ", "मुझे अस्पताल ले चलो", "डॉक्टर को दिखाना है", "mujhe doctor chahiye", "doctor ko bulao", "mujhe hospital le chalo", "doctor ko dikhana hai"],
        "mr": ["मला डॉक्टर हवे", "डॉक्टरांना बोलवा", "मला दवाखान्यात घेऊन चला", "डॉक्टरांना दाखवायचे आहे", "mala doctor have", "doctorna bolva", "mala davakhanyat ghevun chala", "doctorna dakhvayche ahe"],
    },
    "express_happiness": {
        "en": ["I am happy", "I feel very good today", "I am so glad", "This makes me happy", "I feel great", "I am feeling joyful", "Thank you I am happy"],
        "hi": ["मैं खुश हूँ", "आज मुझे बहुत अच्छा लग रहा है", "मुझे बहुत खुशी हो रही है", "मैं बहुत खुश हूँ", "main khush hoon", "aaj mujhe bahut accha lag raha hai", "mujhe bahut khushi ho rahi hai", "main bahut khush hoon"],
        "mr": ["मी आनंदी आहे", "आज मला खूप छान वाटतंय", "मला खूप आनंद झाला", "मी खूप खुश आहे", "mi anandi ahe", "aaj mala khup chan vatatay", "mala khup anand zala", "mi khup khush ahe"],
    },
    "express_sadness": {
        "en": ["I am sad", "I feel very low", "I feel like crying", "I am feeling depressed", "I am unhappy", "I feel lonely and sad", "I am upset"],
        "hi": ["मैं उदास हूँ", "मुझे बहुत बुरा लग रहा है", "मुझे रोना आ रहा है", "मैं दुखी हूँ", "main udaas hoon", "mujhe bahut bura lag raha hai", "mujhe rona aa raha hai", "main dukhi hoon"],
        "mr": ["मी दुःखी आहे", "मला खूप वाईट वाटतंय", "मला रडू येत आहे", "मी उदास आहे", "mi dukhi ahe", "mala khup vait vatatay", "mala radu yet ahe", "mi udas ahe"],
    },
    "express_fear": {
        "en": ["I am scared", "I feel afraid", "I am frightened", "I am very scared", "I feel fear", "This scares me", "I am anxious and afraid"],
        "hi": ["मुझे डर लग रहा है", "मैं डर गया हूँ", "मुझे बहुत डर लग रहा है", "मुझे घबराहट हो रही है", "mujhe dar lag raha hai", "main dar gaya hoon", "mujhe bahut dar lag raha hai", "mujhe ghabrahat ho rahi hai"],
        "mr": ["मला भीती वाटते", "मी घाबरलो आहे", "मला खूप भीती वाटत आहे", "मला घाबरल्यासारखे वाटते", "mala bhiti vatte", "mi ghabarlo ahe", "mala khup bhiti vatat ahe", "mala ghabaralyasarkhe vatte"],
    },
    "request_wait": {
        "en": ["Please wait", "Wait a moment", "Give me a minute", "Hold on", "Just a second", "Wait for me", "Please be patient"],
        "hi": ["रुको", "थोड़ा रुकिए", "एक मिनट ठहरो", "मेरा इंतज़ार करो", "ruko", "thoda ruko", "ek minute ruko", "mera intezaar karo"],
        "mr": ["थांबा", "थोडा थांबा", "एक मिनिट थांबा", "माझी वाट पहा", "thamba", "thoda thamba", "ek minit thamba", "majhi vaat paha"],
    },
    "request_go_home": {
        "en": ["I want to go home", "Take me home", "I want to return home", "Please send me home", "I want to leave and go home", "Let me go home", "Home please"],
        "hi": ["मुझे घर जाना है", "मुझे घर ले चलो", "मैं घर वापस जाना चाहता हूँ", "घर भेज दो", "mujhe ghar jana hai", "mujhe ghar le chalo", "main ghar wapas jana chahta hoon", "ghar bhej do"],
        "mr": ["मला घरी जायचे आहे", "मला घरी घेऊन चला", "मला घरी परत जायचे आहे", "मला घरी पाठवा", "mala ghari jayche ahe", "mala ghari ghevun chala", "mala ghari parat jayche ahe", "mala ghari pathva"],
    },
    "request_go_outside": {
        "en": ["I need to go outside", "Take me outside", "I want some fresh air", "I want to go out", "Let me step outside", "Open the door I want to go out", "Outside please"],
        "hi": ["मुझे बाहर जाना है", "मुझे बाहर ले चलो", "मुझे ताज़ी हवा चाहिए", "दरवाजा खोलो मुझे बाहर जाना है", "mujhe bahar jana hai", "mujhe bahar le chalo", "mujhe taazi hawa chahiye", "darwaza kholo mujhe bahar jana hai"],
        "mr": ["मला बाहेर जायचे आहे", "मला बाहेर घेऊन चला", "मला ताजी हवा हवी", "दार उघडा मला बाहेर जायचे आहे", "mala bahere jayche ahe", "mala bahere ghevun chala", "mala taji hava havi", "dar ughda mala bahere jayche ahe"],
    },
    "other_unclear": {
        "en": ["hello", "hello testing one two three", "what is the time", "how is the weather today", "okay", "good morning", "thank you", "tell me a story", "what is your name", "the television is on", "hmm", "I do not know", "maybe later", "see you tomorrow"],
        "hi": ["नमस्ते", "आज मौसम कैसा है", "समय क्या हुआ है", "ठीक है", "namaste", "aaj mausam kaisa hai", "samay kya hua hai", "theek hai", "kuch nahi", "pata nahi"],
        "mr": ["नमस्कार", "आज हवामान कसे आहे", "किती वाजले", "ठीक आहे", "namaskar", "aaj havaman kase ahe", "kiti vajle", "theek ahe", "kahi nahi", "mala mahit nahi"],
    },
}


def rows() -> list:
    """Flatten to (text, intent, language) tuples."""
    out = []
    for intent, langs in PHRASES.items():
        for lang, items in langs.items():
            for text in items:
                out.append((text, intent, lang))
    return out


# Curated keyword lexicon (single words / short stems per intent). Used as extra
# *training-only* examples so the classifier knows core vocabulary such as
# "thirsty", "tahan", "भूक". Written separately from PHRASES above.
KEYWORDS = {
    "request_water": ["water", "thirsty", "thirst", "drink", "पानी", "पाणी", "प्यास", "तहान", "paani", "pani", "pyaas", "tahan", "jal"],
    "request_food": ["food", "hungry", "hunger", "eat", "meal", "lunch", "dinner", "breakfast", "खाना", "भूख", "भूक", "जेवण", "khana", "bhookh", "bhook", "jevan", "roti", "chai"],
    "request_help": ["help", "assist", "assistance", "मदत", "मदद", "सहायता", "सहाय्य", "madad", "madat", "sahayata"],
    "request_bathroom": ["bathroom", "toilet", "washroom", "restroom", "शौचालय", "बाथरूम", "संडास", "टॉयलेट", "sandas", "shauchalay", "latrine"],
    "express_pain": ["pain", "hurts", "painful", "ache", "दर्द", "वेदना", "दुखणे", "dard", "vedna", "dukhat", "dukhtay"],
    "request_medicine": ["medicine", "tablet", "pill", "pills", "dose", "दवा", "दवाई", "औषध", "गोळी", "गोली", "dawa", "dawai", "aushadh", "goli"],
    "request_rest": ["rest", "tired", "exhausted", "weak", "आराम", "थक", "थकलो", "विश्रांती", "aaram", "thak", "thakalo", "vishranti"],
    "request_sleep": ["sleep", "sleepy", "bed", "नींद", "सोना", "झोप", "झोपायचे", "neend", "sona", "zop", "zopaychay"],
    "express_discomfort": ["uncomfortable", "uneasy", "restless", "unwell", "बेचैनी", "असहज", "अस्वस्थ", "अवघडल्यासारखे", "bechaini", "asahaj", "asvasth", "avghadlyasarkhe"],
    "request_family": ["family", "son", "daughter", "relatives", "wife", "husband", "परिवार", "घरवाले", "कुटुंब", "घरच्यांशी", "parivar", "kutumb", "gharwale"],
    "request_emergency_help": ["emergency", "ambulance", "urgent", "serious", "इमरजेंसी", "एम्बुलेंस", "आणीबाणी", "रुग्णवाहिका", "तातडीने", "turant", "anibani", "tatadine", "jaldi"],
    "request_doctor": ["doctor", "hospital", "डॉक्टर", "अस्पताल", "दवाखाना", "दवाखान्यात", "doctorna", "hospital", "davakhana"],
    "express_happiness": ["happy", "glad", "joy", "joyful", "great", "खुश", "खुशी", "आनंदी", "आनंद", "khush", "khushi", "anandi", "anand"],
    "express_sadness": ["sad", "unhappy", "depressed", "crying", "lonely", "उदास", "दुखी", "दुःखी", "रडू", "रोना", "udaas", "udas", "dukhi", "radu", "rona"],
    "express_fear": ["scared", "afraid", "fear", "frightened", "anxious", "डर", "घबराहट", "भीती", "घाबरलो", "dar", "ghabrahat", "bhiti", "ghabarlo"],
    "request_wait": ["wait", "hold", "moment", "second", "रुको", "ठहरो", "थांबा", "ruko", "thamba", "thehro", "ruk"],
    "request_go_home": ["home", "घर", "घरी", "ghar", "ghari", "ghari jayche", "ghar jana"],
    "request_go_outside": ["outside", "outdoors", "fresh air", "बाहर", "बाहेर", "ताज़ी हवा", "bahar", "bahere", "hawa", "hava"],
}


def keyword_rows() -> list:
    out = []
    for intent, words in KEYWORDS.items():
        for w in words:
            out.append((w, intent, "kw"))
    return out
