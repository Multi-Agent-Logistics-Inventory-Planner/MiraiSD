import 'dotenv/config'
import { createClient } from '@supabase/supabase-js'

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