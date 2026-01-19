import dotenv from 'dotenv'
import { fileURLToPath } from 'url'
import { dirname, join, resolve } from 'path'
import { createClient } from '@supabase/supabase-js'

// Load .env from project root (parent directory)
const __filename = fileURLToPath(import.meta.url)
const __dirname = dirname(__filename)
const projectRoot = resolve(__dirname, '..')
const envPath = resolve(projectRoot, '.env')
const result = dotenv.config({ path: envPath })

if (result.error) {
  console.error(`Failed to load .env from ${envPath}:`, result.error.message)
  console.error(`Make sure .env file exists. You can copy env.template to .env and fill in your values.`)
  process.exit(1)
}

const {
  SUPABASE_URL,
  SUPABASE_ANON_KEY,
  EMAIL,
  PASSWORD,
} = process.env

if (!SUPABASE_URL || !SUPABASE_ANON_KEY || !EMAIL || !PASSWORD) {
  console.error('Missing env vars: SUPABASE_URL, SUPABASE_ANON_KEY, EMAIL, PASSWORD')
  process.exit(1)
}

const supabase = createClient(SUPABASE_URL, SUPABASE_ANON_KEY)

const { data, error } = await supabase.auth.signInWithPassword({
  email: EMAIL,
  password: PASSWORD,
})

if (error) {
  console.error(error)
  process.exit(1)
}

console.log(data.session.access_token)