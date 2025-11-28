/**
 * Translate Android strings.xml to multiple languages using Google Translate API
 * 
 * Usage:
 *   1. Set your API key: set GOOGLE_TRANSLATE_API_KEY=your-key-here
 *   2. Run: node scripts/translate-strings.js
 */

const https = require('https');
const fs = require('fs');
const path = require('path');

// Your Google Translate API key (set via environment variable)
const API_KEY = process.env.GOOGLE_TRANSLATE_API_KEY;

if (!API_KEY) {
    console.error('ERROR: Please set GOOGLE_TRANSLATE_API_KEY environment variable');
    console.error('Example: set GOOGLE_TRANSLATE_API_KEY=your-api-key-here');
    process.exit(1);
}

// Strings to translate (from English)
const stringsToTranslate = {
    disclaimer_title: "Important Notice",
    disclaimer_text: `This app uses crowdsourced data from OpenStreetMap to display speed limits. This data may be incomplete, inaccurate, or outdated.

WE MAKE NO WARRANTIES regarding the accuracy, reliability, or completeness of any speed limit information displayed.

YOU ARE SOLELY RESPONSIBLE for obeying all traffic laws and posted speed limits. Always refer to official road signs as the authoritative source.

By using this app, you acknowledge that the developers, contributors, and data providers accept NO LIABILITY for any consequences arising from reliance on information displayed, including but not limited to traffic violations, accidents, injuries, or damages of any kind.`,
    disclaimer_safety: `🚗 SAFETY FIRST

NEVER interact with this app while driving. Set up before you start your journey and pull over safely if you need to make any changes.

Your safety and the safety of others is your responsibility.`,
    disclaimer_accept: "I Understand & Accept",
    disclaimer_reject: "Decline"
};

// Target languages (ISO 639-1 codes) - ALL Android-supported languages
const targetLanguages = [
    // === ALREADY TRANSLATED (will be skipped if folder exists) ===
    { code: 'ar', folder: 'values-ar', name: 'Arabic' },
    { code: 'bg', folder: 'values-bg', name: 'Bulgarian' },
    { code: 'bn', folder: 'values-bn', name: 'Bengali' },
    { code: 'ca', folder: 'values-ca', name: 'Catalan' },
    { code: 'cs', folder: 'values-cs', name: 'Czech' },
    { code: 'da', folder: 'values-da', name: 'Danish' },
    { code: 'de', folder: 'values-de', name: 'German' },
    { code: 'el', folder: 'values-el', name: 'Greek' },
    { code: 'es', folder: 'values-es', name: 'Spanish' },
    { code: 'et', folder: 'values-et', name: 'Estonian' },
    { code: 'fa', folder: 'values-fa', name: 'Persian' },
    { code: 'fi', folder: 'values-fi', name: 'Finnish' },
    { code: 'fr', folder: 'values-fr', name: 'French' },
    { code: 'he', folder: 'values-iw', name: 'Hebrew' },
    { code: 'hi', folder: 'values-hi', name: 'Hindi' },
    { code: 'hr', folder: 'values-hr', name: 'Croatian' },
    { code: 'hu', folder: 'values-hu', name: 'Hungarian' },
    { code: 'id', folder: 'values-in', name: 'Indonesian' },
    { code: 'it', folder: 'values-it', name: 'Italian' },
    { code: 'ja', folder: 'values-ja', name: 'Japanese' },
    { code: 'ko', folder: 'values-ko', name: 'Korean' },
    { code: 'lt', folder: 'values-lt', name: 'Lithuanian' },
    { code: 'lv', folder: 'values-lv', name: 'Latvian' },
    { code: 'ms', folder: 'values-ms', name: 'Malay' },
    { code: 'nb', folder: 'values-nb', name: 'Norwegian' },
    { code: 'nl', folder: 'values-nl', name: 'Dutch' },
    { code: 'pl', folder: 'values-pl', name: 'Polish' },
    { code: 'pt', folder: 'values-pt', name: 'Portuguese' },
    { code: 'ro', folder: 'values-ro', name: 'Romanian' },
    { code: 'ru', folder: 'values-ru', name: 'Russian' },
    { code: 'sk', folder: 'values-sk', name: 'Slovak' },
    { code: 'sl', folder: 'values-sl', name: 'Slovenian' },
    { code: 'sr', folder: 'values-sr', name: 'Serbian' },
    { code: 'sv', folder: 'values-sv', name: 'Swedish' },
    { code: 'th', folder: 'values-th', name: 'Thai' },
    { code: 'tr', folder: 'values-tr', name: 'Turkish' },
    { code: 'uk', folder: 'values-uk', name: 'Ukrainian' },
    { code: 'vi', folder: 'values-vi', name: 'Vietnamese' },
    { code: 'zh-CN', folder: 'values-zh-rCN', name: 'Chinese (Simplified)' },
    { code: 'zh-TW', folder: 'values-zh-rTW', name: 'Chinese (Traditional)' },
    
    // === NEW LANGUAGES TO ADD ===
    // Celtic & British Isles
    { code: 'cy', folder: 'values-cy', name: 'Welsh' },
    { code: 'ga', folder: 'values-ga', name: 'Irish' },
    { code: 'gd', folder: 'values-gd', name: 'Scottish Gaelic' },
    
    // Nordic & Baltic
    { code: 'is', folder: 'values-is', name: 'Icelandic' },
    { code: 'fo', folder: 'values-fo', name: 'Faroese' },
    
    // African
    { code: 'sw', folder: 'values-sw', name: 'Swahili' },
    { code: 'af', folder: 'values-af', name: 'Afrikaans' },
    { code: 'zu', folder: 'values-zu', name: 'Zulu' },
    { code: 'xh', folder: 'values-xh', name: 'Xhosa' },
    { code: 'am', folder: 'values-am', name: 'Amharic' },
    { code: 'ha', folder: 'values-ha', name: 'Hausa' },
    { code: 'yo', folder: 'values-yo', name: 'Yoruba' },
    { code: 'ig', folder: 'values-ig', name: 'Igbo' },
    { code: 'so', folder: 'values-so', name: 'Somali' },
    
    // South Asian
    { code: 'ta', folder: 'values-ta', name: 'Tamil' },
    { code: 'te', folder: 'values-te', name: 'Telugu' },
    { code: 'ml', folder: 'values-ml', name: 'Malayalam' },
    { code: 'kn', folder: 'values-kn', name: 'Kannada' },
    { code: 'mr', folder: 'values-mr', name: 'Marathi' },
    { code: 'gu', folder: 'values-gu', name: 'Gujarati' },
    { code: 'pa', folder: 'values-pa', name: 'Punjabi' },
    { code: 'ur', folder: 'values-ur', name: 'Urdu' },
    { code: 'ne', folder: 'values-ne', name: 'Nepali' },
    { code: 'si', folder: 'values-si', name: 'Sinhala' },
    
    // Southeast Asian
    { code: 'tl', folder: 'values-tl', name: 'Filipino/Tagalog' },
    { code: 'km', folder: 'values-km', name: 'Khmer' },
    { code: 'lo', folder: 'values-lo', name: 'Lao' },
    { code: 'my', folder: 'values-my', name: 'Burmese' },
    
    // Central Asian & Caucasus
    { code: 'ka', folder: 'values-ka', name: 'Georgian' },
    { code: 'hy', folder: 'values-hy', name: 'Armenian' },
    { code: 'az', folder: 'values-az', name: 'Azerbaijani' },
    { code: 'kk', folder: 'values-kk', name: 'Kazakh' },
    { code: 'uz', folder: 'values-uz', name: 'Uzbek' },
    { code: 'ky', folder: 'values-ky', name: 'Kyrgyz' },
    { code: 'mn', folder: 'values-mn', name: 'Mongolian' },
    
    // European (additional)
    { code: 'sq', folder: 'values-sq', name: 'Albanian' },
    { code: 'mk', folder: 'values-mk', name: 'Macedonian' },
    { code: 'bs', folder: 'values-bs', name: 'Bosnian' },
    { code: 'be', folder: 'values-be', name: 'Belarusian' },
    { code: 'eu', folder: 'values-eu', name: 'Basque' },
    { code: 'gl', folder: 'values-gl', name: 'Galician' },
    { code: 'mt', folder: 'values-mt', name: 'Maltese' },
    { code: 'lb', folder: 'values-lb', name: 'Luxembourgish' },
    
    // Other
    { code: 'eo', folder: 'values-eo', name: 'Esperanto' },
    { code: 'la', folder: 'values-la', name: 'Latin' },
];

// Google Translate API call
function translateText(text, targetLang) {
    return new Promise((resolve, reject) => {
        const postData = JSON.stringify({
            q: text,
            source: 'en',
            target: targetLang,
            format: 'text'
        });

        const options = {
            hostname: 'translation.googleapis.com',
            path: `/language/translate/v2?key=${API_KEY}`,
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Content-Length': Buffer.byteLength(postData)
            }
        };

        const req = https.request(options, (res) => {
            let data = '';
            res.on('data', chunk => data += chunk);
            res.on('end', () => {
                try {
                    const json = JSON.parse(data);
                    if (json.data && json.data.translations) {
                        resolve(json.data.translations[0].translatedText);
                    } else if (json.error) {
                        reject(new Error(json.error.message));
                    } else {
                        reject(new Error('Unknown response format'));
                    }
                } catch (e) {
                    reject(e);
                }
            });
        });

        req.on('error', reject);
        req.write(postData);
        req.end();
    });
}

// Escape XML special characters
function escapeXml(text) {
    return text
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '\\"')
        .replace(/'/g, "\\'")
        .replace(/\n/g, '\\n');
}

// Generate strings.xml content
function generateStringsXml(translations, langName) {
    return `<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- ${langName} translations - Disclaimer (Auto-generated via Google Translate) -->
    <string name="disclaimer_title">${escapeXml(translations.disclaimer_title)}</string>
    <string name="disclaimer_text">${escapeXml(translations.disclaimer_text)}</string>
    <string name="disclaimer_safety">${escapeXml(translations.disclaimer_safety)}</string>
    <string name="disclaimer_accept">${escapeXml(translations.disclaimer_accept)}</string>
    <string name="disclaimer_reject">${escapeXml(translations.disclaimer_reject)}</string>
</resources>
`;
}

// Main translation process
async function translateAllLanguages() {
    const resPath = path.join(__dirname, '..', 'app', 'src', 'main', 'res');
    
    console.log('Starting translation process...\n');
    console.log(`Translating to ${targetLanguages.length} languages\n`);

    let translated = 0;
    let skipped = 0;
    
    for (const lang of targetLanguages) {
        const folderPath = path.join(resPath, lang.folder);
        const filePath = path.join(folderPath, 'strings.xml');
        
        // Skip if already translated
        if (fs.existsSync(filePath)) {
            console.log(`  ⏭ ${lang.name} - already exists, skipping`);
            skipped++;
            continue;
        }
        
        console.log(`Translating to ${lang.name} (${lang.code})...`);
        
        try {
            const translations = {};
            
            for (const [key, value] of Object.entries(stringsToTranslate)) {
                translations[key] = await translateText(value, lang.code);
                // Small delay to avoid rate limiting
                await new Promise(r => setTimeout(r, 100));
            }
            
            // Create directory if it doesn't exist
            if (!fs.existsSync(folderPath)) {
                fs.mkdirSync(folderPath, { recursive: true });
            }
            
            // Write strings.xml
            const xmlContent = generateStringsXml(translations, lang.name);
            fs.writeFileSync(path.join(folderPath, 'strings.xml'), xmlContent, 'utf8');
            
            console.log(`  ✓ ${lang.name} complete`);
            translated++;
            
        } catch (error) {
            console.error(`  ✗ ${lang.name} failed: ${error.message}`);
        }
    }
    
    console.log(`\nSummary: ${translated} translated, ${skipped} skipped (already exist)`);
    
    console.log('\nTranslation complete!');
    console.log(`Files written to: ${resPath}`);
}

// Run
translateAllLanguages().catch(console.error);

